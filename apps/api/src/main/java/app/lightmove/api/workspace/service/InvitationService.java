package app.lightmove.api.workspace.service;

import app.lightmove.api.billing.seat.service.SeatAllowance;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.model.EmailMessage;
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
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Invitations — the <b>only</b> way into an existing workspace; an admin naming a person is the
 * approval. Any domain may be invited, but the address passes signup's work-email gate.
 *
 * <p>Imperative admin checks, not {@code @PreAuthorize}: it is also reached from the anonymous
 * accept-invitation-signup path, where method security would see no authentication at all.
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
    private final SeatAllowance seats;
    private final Clock clock;

    /** Invites colleagues. Skippable — the wizard's "Skip for now" simply sends an empty list. */
    @Transactional
    public List<Invitation> invite(AuthPrincipal principal, List<InviteCommand> commands,
                                   HttpServletRequest request) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }

        UUID workspaceId = principal.requireWorkspaceId();
        access.requireAdmin(principal.userId(), workspaceId);

        Collection<InviteCommand> invitees = checkedInvitees(commands);

        Workspace workspace = requireWorkspace(workspaceId);
        User inviter = requireUser(principal.userId());

        Instant now = clock.instant();
        Instant expiry = now.plus(properties.auth().invitationTtl());
        List<Invitation> issued = new ArrayList<>(invitees.size());
        Set<String> pendingStaff = pendingStaffInvitees(workspaceId, now);

        for (InviteCommand invitee : invitees) {
            String email = invitee.email();

            // Already staff: skipped rather than failing the other nine invitations in the batch. A pure client
            // is invited, and accepting adds the staff role to the membership they hold.
            if (activeMember(workspaceId, email).filter(WorkspaceMember::holdsStaffRole).isPresent()) {
                log.debug("Skipping invite for {} — already a member", email);
                continue;
            }

            seats.requireRoomFor(workspaceId, 1 + pendingStaff.size() - (pendingStaff.contains(email) ? 1 : 0));
            Role granted = rbac.role(invitee.role());
            IssuedInvitation invitation = issue(workspace.getId(), null, email, granted, inviter.getId(), expiry);
            mailOnceCommitted(templates.buildInvitationEmail(
                    email, inviter.getFullName(), workspace.getName(), granted.getName(), invitation.acceptLink()));
            recordInvited(invitation.invitation(), inviter.getId(), request).detail("role", granted.getName()).record();
            issued.add(invitation.invitation());
            pendingStaff.add(email);
        }

        return issued;
    }

    /**
     * Every row is checked before any is issued, so a bad tenth row refuses the batch with nothing half-sent,
     * and an address listed twice is one invitee — a second would have rotated the first's token.
     */
    private Collection<InviteCommand> checkedInvitees(List<InviteCommand> commands) {
        Map<String, InviteCommand> invitees = new LinkedHashMap<>();
        for (InviteCommand command : commands) {
            String email = EmailAddressValidator.normalise(command.email());
            emailValidator.validateWorkEmail(email);

            // A client is invited to a project, which keeps the invitation CHECK trivially true.
            if (command.role() == WorkspaceRole.CLIENT) {
                throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                        "Representatives are invited to a position, not to the workspace");
            }
            invitees.putIfAbsent(email, new InviteCommand(email, command.role()));
        }
        return invitees.values();
    }

    /**
     * Reissues the outstanding invitation rather than creating a second, rotating the token so no resend
     * leaves another live credential in an inbox. {@code clientId} null is a staff invitation.
     */
    private IssuedInvitation issue(UUID workspaceId, UUID clientId, String email, Role role, UUID invitedBy,
                                   Instant expiry) {
        String plaintext = Tokens.generate();
        String hash = Tokens.hash(plaintext);

        Optional<Invitation> pending = clientId == null
                ? invitations.findByWorkspaceIdAndEmailAndClientIdIsNullAndStatus(
                        workspaceId, email, InvitationStatus.PENDING)
                : invitations.findByWorkspaceIdAndClientIdAndEmailAndStatus(
                        workspaceId, clientId, email, InvitationStatus.PENDING);
        Invitation invitation = pending
                .map(existing -> {
                    existing.reissue(role, hash, expiry);
                    return existing;
                })
                .orElseGet(() -> invitations.save(clientId == null
                        ? Invitation.create(workspaceId, email, role, hash, invitedBy, expiry)
                        : Invitation.createForClient(workspaceId, clientId, email, role, hash, invitedBy, expiry)));

        return new IssuedInvitation(invitation, acceptLink(plaintext));
    }

    private record IssuedInvitation(Invitation invitation, String acceptLink) {

        @Override
        public String toString() {
            return "IssuedInvitation[invitation=" + invitation.getId() + ", acceptLink=<redacted>]";
        }
    }

    private AuditService.Builder recordInvited(Invitation invitation, UUID inviterId, HttpServletRequest request) {
        return audit.event(WorkspaceEventType.MEMBER_INVITED)
                .actor(inviterId).workspace(invitation.getWorkspaceId())
                .target("invitation", invitation.getId()).from(request)
                .detail("email", invitation.getEmail());
    }

    /**
     * An invitation's link is mailed only once its row has committed: mailed from inside the transaction, a
     * later row's refusal rolled the invitation back and left a link answering "invalid invitation".
     */
    private void mailOnceCommitted(EmailMessage message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            emailSender.send(message);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                emailSender.send(message);
            }
        });
    }

    /**
     * The sanctioned project-to-workspace seam, taking primitives only. The calling controller gates on
     * {@code CLIENT_RECORD_MANAGE}; the work-email rule still applies.
     */
    @Transactional
    public Invitation inviteClientRepresentative(UUID workspaceId, UUID clientId, String clientName,
                                                 String rawEmail, UUID invitedBy, HttpServletRequest request) {
        String email = EmailAddressValidator.normalise(rawEmail);
        emailValidator.validateWorkEmail(email);

        Workspace workspace = requireWorkspace(workspaceId);
        User inviter = requireUser(invitedBy);

        IssuedInvitation issued = issue(workspaceId, clientId, email, rbac.role(WorkspaceRole.CLIENT), invitedBy,
                clock.instant().plus(properties.auth().invitationTtl()));
        mailOnceCommitted(templates.buildClientInvitationEmail(
                email, inviter.getFullName(), workspace.getName(), clientName, issued.acceptLink()));
        recordInvited(issued.invitation(), invitedBy, request)
                .detail("type", "client").detail("clientId", clientId.toString())
                .record();

        return issued.invitation();
    }

    /** An active member gains the CLIENT role on their membership; a stranger gets an invitation. */
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

    @Transactional(readOnly = true)
    public long countPendingStaff(UUID workspaceId) {
        return invitations.countByWorkspaceIdAndClientIdIsNullAndStatus(workspaceId, InvitationStatus.PENDING);
    }

    /** Staff invitations only: a client-rep invitation never surfaces, nor is reachable by revoke/resend. */
    @Transactional(readOnly = true)
    public List<Invitation> pending(UUID userId, UUID workspaceId) {
        access.requireAdmin(userId, workspaceId);
        return invitations.findByWorkspaceIdAndClientIdIsNullAndStatus(workspaceId, InvitationStatus.PENDING);
    }

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
        Role role = invitation.getRole();

        IssuedInvitation issued = issue(workspaceId, null, invitation.getEmail(), role, inviter.getId(),
                clock.instant().plus(properties.auth().invitationTtl()));
        mailOnceCommitted(templates.buildInvitationEmail(
                invitation.getEmail(), inviter.getFullName(), workspace.getName(), role.getName(),
                issued.acceptLink()));
        recordInvited(issued.invitation(), inviter.getId(), request).detail("role", role.getName()).record();
    }

    private Invitation requirePendingInvitation(UUID workspaceId, UUID invitationId) {
        return invitations.findById(invitationId)
                .filter(inv -> inv.getWorkspaceId().equals(workspaceId))
                .filter(inv -> inv.getStatus() == InvitationStatus.PENDING)
                // A client-rep invitation's id is invisible here, exactly like a foreign workspace's.
                .filter(inv -> inv.getClientId() == null)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private Set<String> pendingStaffInvitees(UUID workspaceId, Instant now) {
        return invitations.findByWorkspaceIdAndClientIdIsNullAndStatus(workspaceId, InvitationStatus.PENDING)
                .stream()
                .filter(invitation -> invitation.isRedeemable(now))
                .map(invitation -> EmailAddressValidator.normalise(invitation.getEmail()))
                .collect(Collectors.toCollection(HashSet::new));
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
