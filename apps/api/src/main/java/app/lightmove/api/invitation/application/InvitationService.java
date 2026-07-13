package app.lightmove.api.invitation.application;

import app.lightmove.api.auth.domain.User;
import app.lightmove.api.auth.infrastructure.UserRepository;
import app.lightmove.api.common.audit.AuditEventType;
import app.lightmove.api.common.audit.AuditService;
import app.lightmove.api.common.config.LightMoveProperties;
import app.lightmove.api.common.error.ApiException;
import app.lightmove.api.common.error.ErrorCode;
import app.lightmove.api.common.security.AuthPrincipal;
import app.lightmove.api.common.security.Tokens;
import app.lightmove.api.email.EmailAddressValidator;
import app.lightmove.api.email.EmailSender;
import app.lightmove.api.email.EmailTemplates;
import app.lightmove.api.invitation.domain.Invitation;
import app.lightmove.api.invitation.domain.InvitationStatus;
import app.lightmove.api.invitation.infrastructure.InvitationRepository;
import app.lightmove.api.workspace.domain.MemberStatus;
import app.lightmove.api.workspace.domain.Workspace;
import app.lightmove.api.workspace.domain.WorkspaceMember;
import app.lightmove.api.workspace.domain.WorkspaceRole;
import app.lightmove.api.workspace.infrastructure.WorkspaceMemberRepository;
import app.lightmove.api.workspace.infrastructure.WorkspaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signup step 3 — an admin naming the people who should be in their workspace.
 *
 * <p>An invitation is the fast path in. Where someone who merely <i>found</i> a workspace on their
 * email domain has to wait for an admin to approve them, an invited person lands active immediately —
 * because an admin naming them <i>is</i> that approval, given up front.
 *
 * <p>Invitees are not restricted to the workspace's own domain. A search firm works with contractors,
 * advisors and associates who have their own addresses, and an admin deliberately naming one of them is
 * exactly the decision this system trusts. The address still has to be a real, non-disposable work
 * address — the consumer-domain rule applies here as it does at signup.
 */
@Service
public class InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);

    private final InvitationRepository invitations;
    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final UserRepository users;
    private final EmailAddressValidator emailValidator;
    private final EmailSender emailSender;
    private final EmailTemplates templates;
    private final AuditService audit;
    private final LightMoveProperties properties;

    public InvitationService(InvitationRepository invitations, WorkspaceRepository workspaces,
                             WorkspaceMemberRepository members, UserRepository users,
                             EmailAddressValidator emailValidator, EmailSender emailSender,
                             EmailTemplates templates, AuditService audit,
                             LightMoveProperties properties) {
        this.invitations = invitations;
        this.workspaces = workspaces;
        this.members = members;
        this.users = users;
        this.emailValidator = emailValidator;
        this.emailSender = emailSender;
        this.templates = templates;
        this.audit = audit;
        this.properties = properties;
    }

    /** Invites colleagues. Skippable — the wizard's "Skip for now" simply sends an empty list. */
    @Transactional
    public List<Invitation> invite(AuthPrincipal principal, List<InviteCommand> commands,
                                   HttpServletRequest request) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }

        UUID workspaceId = principal.requireWorkspaceId();
        requireAdmin(principal.userId(), workspaceId);

        Workspace workspace = workspaces.findById(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));
        User inviter = users.findById(principal.userId())
                .orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));

        Instant expiry = Instant.now().plus(properties.auth().invitationTtl());
        List<Invitation> issued = new ArrayList<>(commands.size());

        for (InviteCommand command : commands) {
            String email = command.email().trim().toLowerCase(Locale.ROOT);

            // Same gate as signup: a real, deliverable, non-disposable work address. Inviting a Gmail
            // account would create a member who can never satisfy the rules the rest of the system
            // applies to them.
            emailValidator.validateWorkEmail(email);

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

        Invitation invitation = invitations
                .findByWorkspaceIdAndEmailAndStatus(workspace.getId(), email, InvitationStatus.PENDING)
                .map(existing -> {
                    existing.refresh(hash, expiry);
                    return existing;
                })
                .orElseGet(() -> invitations.save(Invitation.create(
                        workspace.getId(), email, role, hash, inviter.getId(), expiry)));

        String link = "%s/auth/accept-invite?token=%s".formatted(
                properties.web().baseUrl(),
                URLEncoder.encode(plaintext, StandardCharsets.UTF_8));

        emailSender.send(templates.invitation(
                email, inviter.getFullName(), workspace.getName(), role.name(), link));

        audit.event(AuditEventType.MEMBER_INVITED)
                .actor(inviter.getId()).workspace(workspace.getId())
                .target("invitation", invitation.getId()).from(request)
                .detail("email", email).detail("role", role.name())
                .record();

        return invitation;
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
        Invitation invitation = invitations.findByTokenHash(Tokens.hash(plaintextToken))
                .orElseThrow(() -> ApiException.of(ErrorCode.INVITATION_INVALID));

        if (!invitation.isRedeemable(Instant.now())) {
            throw ApiException.of(invitation.getExpiresAt().isBefore(Instant.now())
                    ? ErrorCode.INVITATION_EXPIRED
                    : ErrorCode.INVITATION_INVALID);
        }

        Workspace workspace = workspaces.findById(invitation.getWorkspaceId())
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));

        String inviterName = users.findById(invitation.getInvitedBy())
                .map(User::getFullName)
                .orElse(null);

        return new InvitationPreview(
                invitation.getEmail(),
                invitation.getRole(),
                workspace.getName(),
                inviterName);
    }

    /** What the invitee is shown before they sign in. See {@link #preview}. */
    public record InvitationPreview(String email, WorkspaceRole role, String workspaceName,
                                    String inviterName) {}

    /**
     * Accepts an invitation. The invitee lands ACTIVE straight away — no approval step, because an
     * admin naming them was the approval.
     *
     * <p>Their email must match the address that was invited. An invitation is addressed to a person,
     * and a link forwarded to somebody else must not let that somebody else in.
     */
    @Transactional
    public WorkspaceMember accept(String plaintextToken, UUID userId, HttpServletRequest request) {
        Instant now = Instant.now();

        Invitation invitation = invitations.findByTokenHash(Tokens.hash(plaintextToken))
                .orElseThrow(() -> ApiException.of(ErrorCode.INVITATION_INVALID));

        if (!invitation.isRedeemable(now)) {
            throw ApiException.of(invitation.getExpiresAt().isBefore(now)
                    ? ErrorCode.INVITATION_EXPIRED
                    : ErrorCode.INVITATION_INVALID);
        }

        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));

        if (!user.getEmail().equalsIgnoreCase(invitation.getEmail())) {
            audit.event(AuditEventType.INVITATION_ACCEPTED).failed().actor(userId)
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

        if (members.findByUserIdAndStatus(userId, MemberStatus.ACTIVE).isPresent()) {
            throw ApiException.of(ErrorCode.ALREADY_IN_WORKSPACE);
        }

        invitation.accept(userId, now);
        WorkspaceMember member = members.save(WorkspaceMember.invite(
                invitation.getWorkspaceId(), userId, invitation.getRole(), invitation.getInvitedBy()));

        log.info("User {} accepted invitation to workspace {} as {}",
                userId, invitation.getWorkspaceId(), invitation.getRole());

        audit.event(AuditEventType.INVITATION_ACCEPTED)
                .actor(userId).workspace(invitation.getWorkspaceId())
                .target("invitation", invitation.getId()).from(request)
                .detail("role", invitation.getRole().name())
                .record();

        return member;
    }

    /**
     * Re-read from the database rather than trusted from the JWT's role claim: the token was minted up
     * to 15 minutes ago and this admin may have been demoted since. Issuing an invitation grants access
     * to candidate PII, so a stale claim is not good enough.
     */
    private void requireAdmin(UUID userId, UUID workspaceId) {
        WorkspaceMember member = members
                .findByWorkspaceIdAndUserIdAndStatus(workspaceId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_A_MEMBER));

        if (member.getRole() != WorkspaceRole.ADMIN) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only an admin may invite members");
        }
    }

    private boolean isAlreadyMember(UUID workspaceId, String email) {
        return users.findByEmail(email)
                .flatMap(user -> members.findByWorkspaceIdAndUserIdAndStatus(
                        workspaceId, user.getId(), MemberStatus.ACTIVE))
                .isPresent();
    }
}
