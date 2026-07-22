package app.lightmove.api.workspace.service;

import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.RbacService;
import app.lightmove.api.core.security.rbac.Role;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.security.service.AuthService;
import app.lightmove.api.core.security.token.TokenService;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.workspace.constant.InvitationStatus;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.model.ClientRepresentativeAcceptedEvent;
import app.lightmove.api.workspace.model.ClientRepresentativeOnboarding;
import app.lightmove.api.workspace.model.Invitation;
import app.lightmove.api.workspace.model.InviteCommand;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.InvitationRepository;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invitations — the <b>only</b> way into an existing workspace.
 *
 * <p>An admin naming a person <i>is</i> the approval, given up front, so an invited person lands
 * active immediately. There is no queue and no waiting state; membership is invitation-only.
 *
 * <p>Invitees are not restricted to the workspace's own domain. A search firm works with contractors,
 * advisors and associates who have their own addresses, and an admin deliberately naming one of them is
 * exactly the decision this system trusts. The address still has to be a real, non-disposable work
 * address — the consumer-domain rule applies here as it does at signup.
 *
 * <p>Keeps its own imperative admin checks rather than {@code @PreAuthorize}: it is called both from
 * controllers and from {@code PendingOnboardingMaterialiser} with a synthetic principal, outside any
 * request's SecurityContext, where method security would evaluate the wrong authentication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationService {

    private final InvitationRepository invitations;
    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final WorkspaceAccess access;
    private final RbacService rbac;
    private final UserRepository users;
    private final EmailAddressValidator emailValidator;
    private final EmailSender emailSender;
    private final EmailTemplates templates;
    private final AuditService audit;
    private final LightMoveProperties properties;
    private final AuthService authService;
    private final TokenService tokens;
    private final RateLimitGuard rateLimit;
    private final ApplicationEventPublisher events;

    /** Invites colleagues. Skippable — the wizard's "Skip for now" simply sends an empty list. */
    @Transactional
    public List<Invitation> invite(AuthPrincipal principal, List<InviteCommand> commands,
                                   HttpServletRequest request) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }

        UUID workspaceId = principal.requireWorkspaceId();
        access.requireAdmin(principal.userId(), workspaceId);

        Workspace workspace = workspaces.findById(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));
        User inviter = users.findById(principal.userId())
                .orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));

        Instant expiry = Instant.now().plus(properties.auth().invitationTtl());
        List<Invitation> issued = new ArrayList<>(commands.size());

        for (InviteCommand command : commands) {
            String email = EmailAddressValidator.normalise(command.email());

            // Same gate as signup: a real, deliverable, non-disposable work address. Inviting a Gmail
            // account would create a member who can never satisfy the rules the rest of the system
            // applies to them.
            emailValidator.validateWorkEmail(email);

            // CLIENT is groundwork: the role exists in the catalog, but a client is invited to a
            // project, not to the workspace — that flow (and its portal) is a later phase. Refusing
            // here keeps the invitation CHECK's client⇔project rule trivially true until then.
            if (command.role() == WorkspaceRole.CLIENT) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Clients are invited to a project, not to the workspace");
            }

            // Already in. Skipped rather than failing the batch — re-inviting someone who has already
            // joined is a harmless mistake, not a reason to reject the other nine invitations.
            if (isAlreadyMember(workspaceId, email)) {
                log.debug("Skipping invite for {} — already a member", email);
                continue;
            }

            issued.add(issueOrRefresh(workspace, inviter, email, command.role(), expiry, request));
        }

        return issued;
    }

    /**
     * Re-inviting someone with an outstanding invitation refreshes it rather than creating a second.
     *
     * <p>Refreshing rotates the token, which kills the link in the earlier email. That matters: without
     * it, every resend would leave another live credential sitting in an inbox.
     */
    private Invitation issueOrRefresh(Workspace workspace, User inviter, String email,
                                      WorkspaceRole role, Instant expiry, HttpServletRequest request) {
        String plaintext = Tokens.generate();
        String hash = Tokens.hash(plaintext);
        Role granted = rbac.role(role);

        // Scoped to staff (client_id IS NULL): a client-rep invitation to the same address must not be
        // refreshed into a staff one — that would rotate its token and, worse, leave its CLIENT role and
        // client id in place while the email promises MEMBER.
        Invitation invitation = invitations
                .findByWorkspaceIdAndEmailAndClientIdIsNullAndStatus(
                        workspace.getId(), email, InvitationStatus.PENDING)
                .map(existing -> {
                    existing.refresh(hash, expiry);
                    return existing;
                })
                .orElseGet(() -> invitations.save(Invitation.create(
                        workspace.getId(), email, granted, hash, inviter.getId(), expiry)));

        String link = "%s/auth/accept-invite?token=%s".formatted(
                properties.web().baseUrl(),
                URLEncoder.encode(plaintext, StandardCharsets.UTF_8));

        emailSender.send(templates.buildInvitationEmail(
                email, inviter.getFullName(), workspace.getName(), granted.getName(), link));

        audit.event(WorkspaceEventType.MEMBER_INVITED)
                .actor(inviter.getId()).workspace(workspace.getId())
                .target("invitation", invitation.getId()).from(request)
                .detail("email", email).detail("role", granted.getName())
                .record();

        return invitation;
    }

    /**
     * Invites a client representative to the portal for one client. Called from the project feature's
     * {@code ClientRepresentativeService} — the sanctioned project → workspace seam, since invitations
     * are the only door into a workspace and a representative is a CLIENT-role member. Takes the client
     * id and name as primitives so this feature stays ignorant of the {@code ClientRepresentative} the
     * project side keeps.
     *
     * <p>Not gated here with {@code @PreAuthorize}: the calling controller already gates on
     * {@code CLIENT_RECORD_MANAGE}. The work-email rule still applies — a representative must be reachable
     * at a real address for the invitation to mean anything.
     */
    @Transactional
    public Invitation inviteClientRepresentative(UUID workspaceId, UUID clientId, String clientName,
                                                 String rawEmail, UUID invitedBy, HttpServletRequest request) {
        String email = EmailAddressValidator.normalise(rawEmail);
        emailValidator.validateWorkEmail(email);

        Workspace workspace = workspaces.findById(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));
        User inviter = users.findById(invitedBy)
                .orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));

        String plaintext = Tokens.generate();
        String hash = Tokens.hash(plaintext);
        Role clientRole = rbac.role(WorkspaceRole.CLIENT);
        Instant expiry = Instant.now().plus(properties.auth().invitationTtl());

        Invitation invitation = invitations
                .findByWorkspaceIdAndClientIdAndEmailAndStatus(
                        workspaceId, clientId, email, InvitationStatus.PENDING)
                .map(existing -> {
                    existing.refresh(hash, expiry);
                    return existing;
                })
                .orElseGet(() -> invitations.save(Invitation.createForClient(
                        workspaceId, clientId, email, clientRole, hash, invitedBy, expiry)));

        // The same accept link staff use: the representative sets a password on the accept screen and
        // lands in a session (see acceptWithNewLocalUser). Only the email copy is portal-specific.
        String link = "%s/auth/accept-invite?token=%s".formatted(
                properties.web().baseUrl(),
                URLEncoder.encode(plaintext, StandardCharsets.UTF_8));

        emailSender.send(templates.buildClientInvitationEmail(
                email, inviter.getFullName(), workspace.getName(), clientName, link));

        audit.event(WorkspaceEventType.MEMBER_INVITED)
                .actor(invitedBy).workspace(workspaceId)
                .target("invitation", invitation.getId()).from(request)
                .detail("email", email).detail("type", "client").detail("clientId", clientId.toString())
                .record();

        return invitation;
    }

    /**
     * Onboards a client representative, choosing the path by whether the address is already one of our
     * members. An existing active member skips the invitation entirely — they gain the CLIENT role on
     * their current membership and an informational email, no signup, because a user is unique to a
     * workspace and this person is already in. A stranger gets the ordinary invitation flow. Either way
     * the project side turns the result into its representative row.
     */
    @Transactional
    public ClientRepresentativeOnboarding onboardClientRepresentative(
            UUID workspaceId, UUID clientId, String clientName, String rawEmail, UUID addedBy,
            HttpServletRequest request) {
        String email = EmailAddressValidator.normalise(rawEmail);
        emailValidator.validateWorkEmail(email);

        Optional<WorkspaceMember> existing = users.findByEmail(email)
                .flatMap(user -> members.findByWorkspaceIdAndUserIdAndStatus(
                        workspaceId, user.getId(), MemberStatus.ACTIVE));
        if (existing.isEmpty()) {
            Invitation invitation = inviteClientRepresentative(
                    workspaceId, clientId, clientName, email, addedBy, request);
            return new ClientRepresentativeOnboarding(false, null, invitation);
        }

        WorkspaceMember member = existing.get();
        if (member.getRoles().stream().noneMatch(role -> role.is(WorkspaceRole.CLIENT))) {
            Set<Role> roles = new HashSet<>(member.getRoles());
            roles.add(rbac.role(WorkspaceRole.CLIENT));
            member.changeRoles(roles);
        }

        Workspace workspace = workspaces.findById(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));
        User adder = users.findById(addedBy)
                .orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));
        User recipient = users.findById(member.getUserId())
                .orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));

        emailSender.send(templates.buildRepresentativeAddedEmail(
                email, recipient.getFullName(), adder.getFullName(), workspace.getName(), clientName));

        audit.event(WorkspaceEventType.MEMBER_ROLE_CHANGED)
                .actor(addedBy).workspace(workspaceId).target("member", member.getId()).from(request)
                .detail("addedRole", WorkspaceRole.CLIENT.name()).detail("clientId", clientId.toString())
                .record();

        return new ClientRepresentativeOnboarding(true, member.getUserId(), null);
    }

    /**
     * What an invitation says, to whoever is holding its link — before they have an account, let alone a
     * session.
     *
     * <p>This has to be readable unauthenticated, because the person clicking the link out of their
     * inbox is usually a stranger to us: they need to see who invited them and to what, and the signup
     * form needs to know which address the invitation is addressed to so it can fix it there rather
     * than let them create an account we will then refuse to admit.
     *
     * <p>It discloses a workspace name, an inviter's name and the invited address to a caller holding a
     * 256-bit token that was mailed to that address. Someone with the token has the email; the email
     * already said all three things.
     */
    @Transactional(readOnly = true)
    public InvitationPreview preview(String plaintextToken) {
        Invitation invitation = resolveRedeemable(plaintextToken, Instant.now());

        Workspace workspace = workspaces.findById(invitation.getWorkspaceId())
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));

        String inviterName = users.findById(invitation.getInvitedBy())
                .map(User::getFullName)
                .orElse(null);

        return new InvitationPreview(
                invitation.getEmail(),
                invitation.getRole().getName(),
                workspace.getName(),
                inviterName);
    }

    /** What the invitee is shown before they sign in. See {@link #preview}. */
    public record InvitationPreview(String email, String role, String workspaceName,
                                    String inviterName) {}

    /**
     * Accepts an invitation by its emailed token. The invitee lands ACTIVE straight away — no approval
     * step, because an admin naming them was the approval.
     */
    @Transactional
    public WorkspaceMember accept(String plaintextToken, UUID userId, HttpServletRequest request) {
        Instant now = Instant.now();
        Invitation invitation = resolveRedeemable(plaintextToken, now);
        return redeem(invitation, requireUser(userId), now, request);
    }

    /**
     * Accepts the caller's own outstanding invitation, with no token.
     *
     * <p>The token's only job was proving control of the invited mailbox — and an authenticated,
     * <b>email-verified</b> user whose address matches the invitation has already proven exactly that.
     * Verification subsumes the token. This is what lets an invitee who verified in a fresh tab (where
     * the emailed link's token lives in another tab's sessionStorage) still land in the right
     * workspace instead of being routed into create-your-own.
     */
    @Transactional
    public WorkspaceMember acceptForUser(UUID userId, HttpServletRequest request) {
        Instant now = Instant.now();
        User user = requireUser(userId);

        Invitation invitation = invitations
                .findFirstByEmailAndStatusOrderByCreatedAtDesc(user.getEmail(), InvitationStatus.PENDING)
                .filter(found -> found.isRedeemable(now))
                .orElseThrow(() -> ApiException.of(ErrorCode.INVITATION_INVALID));

        return redeem(invitation, user, now, request);
    }

    /**
     * Accepts an invitation by creating the invited account in one step — the door in for an invitee who
     * has no account yet, which is the common case.
     *
     * <p><b>No email-verification round-trip.</b> The invitation token was mailed only to
     * {@code invitation.email}, so holding it is proof of that mailbox — the same proof verification
     * exists to give. The account's address is the invitation's, never the request's, so the token can
     * only ever mint the identity it was addressed to; that binding, plus the existing-account guard in
     * {@code createVerifiedLocalUser}, is the security of this path. (Contrast the token-based
     * {@link #accept}, which redeems for an <i>already authenticated</i> user and so still demands a
     * verified session.)
     *
     * <p>Plain {@code @Transactional}: if any step fails, the account, membership, invitation-accept and
     * refresh token roll back together. The opposite of {@code login}/{@code rotate}, whose
     * {@code noRollbackFor} exists only to keep a counter on the failure path — there is no such side
     * effect to protect here.
     */
    @Transactional
    public AuthenticatedSession acceptWithNewLocalUser(String plaintextToken, String fullName,
                                                       String password, HttpServletRequest request) {
        Instant now = Instant.now();
        Invitation invitation = resolveRedeemable(plaintextToken, now);
        rateLimit.checkSignup(invitation.getEmail(), request);

        // The email is the invitation's, so the account is bound to exactly the address the token was
        // mailed to. createVerifiedLocalUser rejects an address that already has an account, so the
        // frontend can send that person to log in and accept from a real session rather than silently
        // attaching a second identity.
        User user = authService.createVerifiedLocalUser(
                invitation.getEmail(), fullName, password, request);
        WorkspaceMember member = redeem(invitation, user, now, request);

        return tokens.issue(user, member, request);
    }

    /**
     * The shared tail of both accept paths: the guards, the membership, the audit trail.
     *
     * <p>Their email must match the address that was invited. An invitation is addressed to a person,
     * and a link forwarded to somebody else must not let that somebody else in.
     */
    private WorkspaceMember redeem(Invitation invitation, User user, Instant now,
                                   HttpServletRequest request) {
        if (!user.getEmail().equalsIgnoreCase(invitation.getEmail())) {
            audit.event(WorkspaceEventType.INVITATION_ACCEPTED).failed().actor(user.getId())
                    .workspace(invitation.getWorkspaceId()).from(request)
                    .reason("email_mismatch").record();
            throw new ApiException(ErrorCode.INVITATION_INVALID,
                    "Invitation was addressed to a different email");
        }

        // An unverified address is an unproven claim to be this person. Accepting on it would let
        // whoever intercepted the invitation email walk in as its intended recipient.
        if (!user.isEmailVerified()) {
            throw ApiException.of(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        if (members.findByUserIdAndStatus(user.getId(), MemberStatus.ACTIVE).isPresent()) {
            throw ApiException.of(ErrorCode.ALREADY_IN_WORKSPACE);
        }

        invitation.accept(user.getId(), now);
        WorkspaceMember member = members.save(WorkspaceMember.invite(
                invitation.getWorkspaceId(), user.getId(), Set.of(invitation.getRole()),
                invitation.getInvitedBy()));

        log.info("User {} accepted invitation to workspace {} as {}",
                user.getId(), invitation.getWorkspaceId(), invitation.getRole().getName());

        audit.event(WorkspaceEventType.INVITATION_ACCEPTED)
                .actor(user.getId()).workspace(invitation.getWorkspaceId())
                .target("invitation", invitation.getId()).from(request)
                .detail("role", invitation.getRole().getName())
                .record();

        // A client representative's acceptance: let the project feature flip its representative row to
        // ACTIVE. Published within this transaction, so the membership and the activation commit together.
        if (invitation.getClientId() != null) {
            events.publishEvent(new ClientRepresentativeAcceptedEvent(
                    invitation.getWorkspaceId(), invitation.getClientId(), user.getEmail(), user.getId()));
        }

        return member;
    }

    /**
     * Resolves an invitation from its emailed token, or fails with the reason it cannot be redeemed: an
     * unknown or already-consumed token is {@code INVITATION_INVALID}, a lapsed one
     * {@code INVITATION_EXPIRED}. Shared by preview and every accept path.
     */
    private Invitation resolveRedeemable(String plaintextToken, Instant now) {
        Invitation invitation = invitations.findByTokenHash(Tokens.hash(plaintextToken))
                .orElseThrow(() -> ApiException.of(ErrorCode.INVITATION_INVALID));
        if (!invitation.isRedeemable(now)) {
            throw ApiException.of(invitation.getExpiresAt().isBefore(now)
                    ? ErrorCode.INVITATION_EXPIRED
                    : ErrorCode.INVITATION_INVALID);
        }
        return invitation;
    }

    /**
     * Outstanding <b>staff</b> invitations, for the Settings → Members screen. Client-rep invitations
     * (which carry a client id) are the client portal's concern and never surface on the roster — nor
     * are their ids reachable by the staff revoke/resend below.
     */
    @Transactional(readOnly = true)
    public List<Invitation> pending(UUID userId, UUID workspaceId) {
        access.requireAdmin(userId, workspaceId);
        return invitations.findByWorkspaceIdAndClientIdIsNullAndStatus(workspaceId, InvitationStatus.PENDING);
    }

    /** Withdraws an invitation — the emailed link stops working immediately. */
    @Transactional
    public void revoke(UUID userId, UUID workspaceId, UUID invitationId, HttpServletRequest request) {
        access.requireAdmin(userId, workspaceId);
        Invitation invitation = requirePendingInvitation(workspaceId, invitationId);

        invitation.revoke();

        audit.event(WorkspaceEventType.INVITATION_REVOKED)
                .actor(userId).workspace(workspaceId).target("invitation", invitationId).from(request)
                .detail("email", invitation.getEmail())
                .record();
    }

    /** Resend rotates the token, so the earlier emailed link dies with it. */
    @Transactional
    public void resend(UUID userId, UUID workspaceId, UUID invitationId, HttpServletRequest request) {
        access.requireAdmin(userId, workspaceId);
        Invitation invitation = requirePendingInvitation(workspaceId, invitationId);

        Workspace workspace = workspaces.findById(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));
        User inviter = requireUser(userId);

        issueOrRefresh(workspace, inviter, invitation.getEmail(),
                WorkspaceRole.valueOf(invitation.getRole().getName()),
                Instant.now().plus(properties.auth().invitationTtl()), request);
    }

    private Invitation requirePendingInvitation(UUID workspaceId, UUID invitationId) {
        return invitations.findById(invitationId)
                .filter(inv -> inv.getWorkspaceId().equals(workspaceId))
                .filter(inv -> inv.getStatus() == InvitationStatus.PENDING)
                // Staff management never touches a client-rep invitation: revoking one would strand its
                // representative row at INVITED, and resending one would rotate its token through the
                // staff email template. A client id is invisible here, exactly like a foreign workspace's.
                .filter(inv -> inv.getClientId() == null)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private boolean isAlreadyMember(UUID workspaceId, String email) {
        return users.findByEmail(email)
                .flatMap(user -> members.findByWorkspaceIdAndUserIdAndStatus(
                        workspaceId, user.getId(), MemberStatus.ACTIVE))
                .isPresent();
    }

    private User requireUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));
    }
}
