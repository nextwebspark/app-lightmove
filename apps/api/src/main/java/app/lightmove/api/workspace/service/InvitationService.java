package app.lightmove.api.workspace.service;

import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.RbacService;
import app.lightmove.api.core.security.rbac.Role;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.workspace.constant.InvitationStatus;
import app.lightmove.api.workspace.constant.MemberStatus;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invitations — the <b>only</b> way into an existing workspace. An admin naming a person <i>is</i> the
 * approval, so an invited person lands active immediately; there is no queue and no waiting state.
 *
 * <p>Invitees are not restricted to the workspace's own domain — a firm works with contractors and
 * advisors who have their own addresses — but the address must still be a real, non-disposable work
 * address, the same gate signup applies.
 *
 * <p>Keeps its own imperative admin checks rather than {@code @PreAuthorize}: it is called both from
 * authenticated controllers and from the anonymous
 * {@code /onboarding/accept-invitation-signup} endpoint, outside any request's SecurityContext, where
 * method security would evaluate no authentication at all.
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

    /** Invites colleagues. Skippable — the wizard's "Skip for now" simply sends an empty list. */
    @Transactional
    public List<Invitation> invite(AuthPrincipal principal, List<InviteCommand> commands,
                                   HttpServletRequest request) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }

        UUID workspaceId = principal.requireWorkspaceId();
        access.requireAdmin(principal.userId(), workspaceId);

        Workspace workspace = requireWorkspace(workspaceId);
        User inviter = requireUser(principal.userId());

        Instant expiry = Instant.now().plus(properties.auth().invitationTtl());
        List<Invitation> issued = new ArrayList<>(commands.size());

        for (InviteCommand command : commands) {
            String email = EmailAddressValidator.normalise(command.email());

            // Same gate as signup: a real, deliverable, non-disposable work address.
            emailValidator.validateWorkEmail(email);

            // A client is invited to a project, not to the workspace. Refusing here keeps the
            // invitation CHECK's client-to-project rule trivially true.
            if (command.role() == WorkspaceRole.CLIENT) {
                throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                        "Hiring managers are invited to a position, not to the workspace");
            }

            // Already in: skipped rather than failing the other nine invitations in the batch.
            if (activeMember(workspaceId, email).isPresent()) {
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

        Invitation invitation = invitations
                .findByWorkspaceIdAndEmailAndClientIdIsNullAndStatus(
                        workspace.getId(), email, InvitationStatus.PENDING)
                .map(existing -> {
                    existing.refresh(hash, expiry);
                    return existing;
                })
                .orElseGet(() -> invitations.save(Invitation.create(
                        workspace.getId(), email, granted, hash, inviter.getId(), expiry)));

        emailSender.send(templates.buildInvitationEmail(
                email, inviter.getFullName(), workspace.getName(), granted.getName(), acceptLink(plaintext)));

        audit.event(WorkspaceEventType.MEMBER_INVITED)
                .actor(inviter.getId()).workspace(workspace.getId())
                .target("invitation", invitation.getId()).from(request)
                .detail("email", email).detail("role", granted.getName())
                .record();

        return invitation;
    }

    /**
     * Invites a client representative to the portal for one client — the sanctioned project-to-workspace
     * seam, since invitations are the only door in and a representative is a CLIENT-role member. Takes
     * primitives so this feature stays ignorant of the {@code ClientRepresentative} the project side
     * keeps.
     *
     * <p>Not gated with {@code @PreAuthorize}: the calling controller already gates on
     * {@code CLIENT_RECORD_MANAGE}. The work-email rule still applies.
     */
    @Transactional
    public Invitation inviteClientRepresentative(UUID workspaceId, UUID clientId, String clientName,
                                                 String rawEmail, UUID invitedBy, HttpServletRequest request) {
        String email = EmailAddressValidator.normalise(rawEmail);
        emailValidator.validateWorkEmail(email);

        Workspace workspace = requireWorkspace(workspaceId);
        User inviter = requireUser(invitedBy);

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

        // The same accept link staff use; only the email copy is portal-specific.
        emailSender.send(templates.buildClientInvitationEmail(
                email, inviter.getFullName(), workspace.getName(), clientName, acceptLink(plaintext)));

        audit.event(WorkspaceEventType.MEMBER_INVITED)
                .actor(invitedBy).workspace(workspaceId)
                .target("invitation", invitation.getId()).from(request)
                .detail("email", email).detail("type", "client").detail("clientId", clientId.toString())
                .record();

        return invitation;
    }

    /**
     * Onboards a client representative. An existing active member skips the invitation entirely and
     * gains the CLIENT role on their current membership, because a user is unique to a workspace and
     * this person is already in; a stranger gets the ordinary invitation flow.
     */
    @Transactional
    public ClientRepresentativeOnboarding onboardClientRepresentative(
            UUID workspaceId, UUID clientId, String clientName, String rawEmail, UUID addedBy,
            HttpServletRequest request) {
        String email = EmailAddressValidator.normalise(rawEmail);
        emailValidator.validateWorkEmail(email);

        Optional<WorkspaceMember> existing = activeMember(workspaceId, email);
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

        Workspace workspace = requireWorkspace(workspaceId);
        User adder = requireUser(addedBy);
        User recipient = requireUser(member.getUserId());

        emailSender.send(templates.buildRepresentativeAddedEmail(
                email, recipient.getFullName(), adder.getFullName(), workspace.getName(), clientName));

        audit.event(WorkspaceEventType.MEMBER_ROLE_CHANGED)
                .actor(addedBy).workspace(workspaceId).target("member", member.getId()).from(request)
                .detail("addedRole", WorkspaceRole.CLIENT.name()).detail("clientId", clientId.toString())
                .record();

        return new ClientRepresentativeOnboarding(true, member.getUserId(), null);
    }

    /**
     * Outstanding <b>staff</b> invitations, for the Members screen. Client-rep invitations carry a
     * client id, never surface here, and their ids are not reachable by the revoke/resend below.
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

        Workspace workspace = requireWorkspace(workspaceId);
        User inviter = requireUser(userId);

        issueOrRefresh(workspace, inviter, invitation.getEmail(),
                WorkspaceRole.valueOf(invitation.getRole().getName()),
                Instant.now().plus(properties.auth().invitationTtl()), request);
    }

    private Invitation requirePendingInvitation(UUID workspaceId, UUID invitationId) {
        return invitations.findById(invitationId)
                .filter(inv -> inv.getWorkspaceId().equals(workspaceId))
                .filter(inv -> inv.getStatus() == InvitationStatus.PENDING)
                // A client-rep invitation's id is invisible here, exactly like a foreign workspace's.
                .filter(inv -> inv.getClientId() == null)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private Optional<WorkspaceMember> activeMember(UUID workspaceId, String email) {
        return users.findByEmail(email)
                .flatMap(user -> members.findByWorkspaceIdAndUserIdAndStatus(
                        workspaceId, user.getId(), MemberStatus.ACTIVE));
    }

    private String acceptLink(String plaintextToken) {
        return "%s/auth/accept-invite?token=%s".formatted(
                properties.web().baseUrl(),
                URLEncoder.encode(plaintextToken, StandardCharsets.UTF_8));
    }

    private Workspace requireWorkspace(UUID workspaceId) {
        return workspaces.findById(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private User requireUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));
    }
}
