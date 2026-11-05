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
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.RbacService;
import app.lightmove.api.core.security.rbac.Role;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.persistence.service.RequiresNewTransactions;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.workspace.constant.InvitationStatus;
import app.lightmove.api.workspace.constant.InvitationWithdrawal;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.model.ClientInvitationState;
import app.lightmove.api.workspace.model.ClientInvitationWithdrawn;
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
import java.util.HashMap;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final RateLimitGuard rateLimit;
    private final TransactionTemplate transactions;
    private final RequiresNewTransactions separately;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    /** Addresses one request may name: a firm's whole team pasted at once, never a mailing list. */
    static final int MAX_INVITEES_PER_REQUEST = 50;

    /**
     * Invites colleagues. Skippable — the wizard's "Skip for now" simply sends an empty list. The invitations are
     * written in one transaction and mailed once it has committed, so an outcome can say which emails did not go.
     */
    public InviteOutcome invite(AuthPrincipal principal, List<InviteCommand> commands, HttpServletRequest request) {
        if (commands == null || commands.isEmpty()) {
            return new InviteOutcome(0, List.of());
        }
        if (commands.size() > MAX_INVITEES_PER_REQUEST) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "Invite at most 50 people at once");
        }
        List<OutgoingInvitation> outgoing = transactions.execute(status -> issueAll(principal, commands, request));
        List<String> notDelivered = deliver(outgoing, principal.userId(), request);
        return new InviteOutcome(outgoing.size(), notDelivered);
    }

    /** {@code notDelivered}: the addresses whose invitation is saved but whose email the mail provider refused. */
    public record InviteOutcome(int sent, List<String> notDelivered) {}

    private List<OutgoingInvitation> issueAll(AuthPrincipal principal, List<InviteCommand> commands,
                                              HttpServletRequest request) {
        UUID workspaceId = principal.requireWorkspaceId();
        access.requireAdmin(principal.userId(), workspaceId);

        // Already staff: skipped rather than failing the other nine invitations in the batch. A pure client
        // is invited, and accepting adds the staff role to the membership they hold.
        List<InviteCommand> invitees = checkedInvitees(commands).stream()
                .filter(invitee -> activeMember(workspaceId, invitee.email())
                        .filter(WorkspaceMember::holdsStaffRole).isEmpty())
                .toList();

        Instant now = clock.instant();
        // Seats before the hourly budget: a batch refused for want of seats must not spend the retry's budget.
        if (!invitees.isEmpty()) {
            requireSeatsWithAlso(workspaceId, invitees.stream().map(InviteCommand::email).toList(), now);
        }
        rateLimit.checkInvitations(workspaceId, invitees.stream().map(InviteCommand::email).toList(), request);

        Workspace workspace = requireWorkspace(workspaceId);
        User inviter = requireUser(principal.userId());

        Instant expiry = now.plus(properties.auth().invitationTtl());
        List<OutgoingInvitation> issued = new ArrayList<>(invitees.size());

        for (InviteCommand invitee : invitees) {
            String email = invitee.email();
            Role granted = rbac.role(invitee.role());
            IssuedInvitation invitation = issue(workspace.getId(), null, email, granted, inviter.getId(), expiry);
            recordInvited(invitation.invitation(), inviter.getId(), request).detail("role", granted.getName()).record();
            issued.add(new OutgoingInvitation(invitation.invitation().getId(), workspaceId, email,
                    templates.buildInvitationEmail(email, inviter.getFullName(), workspace.getName(),
                            granted.getName(), invitation.acceptLink())));
        }
        return issued;
    }

    /** An invitation written and committed, with the email that carries its link. */
    private record OutgoingInvitation(UUID invitationId, UUID workspaceId, String email, EmailMessage message) {

        @Override
        public String toString() {
            return "OutgoingInvitation[invitation=" + invitationId + ", message=<redacted>]";
        }
    }

    /**
     * Sends each committed invitation's email, outside any transaction. A refused send stays a saved invitation the
     * Team page marks, so an admin can resend it, rather than a silent "sent".
     */
    private List<String> deliver(List<OutgoingInvitation> outgoing, UUID actorId, HttpServletRequest request) {
        List<OutgoingInvitation> refused = outgoing.stream()
                .filter(invitation -> !emailSender.trySend(invitation.message()))
                .toList();
        if (refused.isEmpty()) {
            return List.of();
        }
        Instant now = clock.instant();
        // On its own transaction: deliver also runs from afterCommit, where a joined one has already committed.
        separately.run(() -> refused.forEach(invitation -> {
            invitations.findById(invitation.invitationId()).ifPresent(found -> found.markEmailFailed(now));
            audit.event(WorkspaceEventType.INVITATION_EMAIL_FAILED)
                    .failed().actor(actorId).workspace(invitation.workspaceId())
                    .target("invitation", invitation.invitationId()).from(request)
                    .record();
        }));
        return refused.stream().map(OutgoingInvitation::email).toList();
    }

    /**
     * Every address and role is checked before any is issued. An address listed twice is one invitee — a second
     * would have rotated the first's token — and twice with two roles is refused rather than one silently kept.
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
            InviteCommand listed = invitees.putIfAbsent(email, new InviteCommand(email, command.role()));
            if (listed != null && listed.role() != command.role()) {
                throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                        "An address is listed twice with different roles");
            }
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
                    existing.reissue(role, hash, expiry, invitedBy);
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
     * Mailed only once the caller's transaction has committed: mailed from inside it, a later refusal rolled the
     * invitation back and left a link answering "invalid invitation" — or told someone they now represent a client
     * whose record never kept them.
     */
    private void mailOnceCommitted(Runnable send) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send.run();
            }
        });
    }

    /** A client contact's invitation, mailed after commit; a refused send is marked as a staff one is. */
    private void mailClientInvitationOnceCommitted(OutgoingInvitation outgoing, UUID actorId,
                                                   HttpServletRequest request) {
        mailOnceCommitted(() -> deliver(List.of(outgoing), actorId, request));
    }

    private Invitation inviteClientRepresentative(UUID workspaceId, UUID clientId, String clientName,
                                                 String rawEmail, UUID invitedBy, HttpServletRequest request) {
        String email = EmailAddressValidator.normalise(rawEmail);
        emailValidator.validateWorkEmail(email);

        Workspace workspace = requireWorkspace(workspaceId);
        User inviter = requireUser(invitedBy);

        IssuedInvitation issued = issue(workspaceId, clientId, email, rbac.role(WorkspaceRole.CLIENT), invitedBy,
                clock.instant().plus(properties.auth().invitationTtl()));
        mailClientInvitationOnceCommitted(new OutgoingInvitation(issued.invitation().getId(), workspaceId, email,
                templates.buildClientInvitationEmail(
                        email, inviter.getFullName(), workspace.getName(), clientName, issued.acceptLink())),
                invitedBy, request);
        recordInvited(issued.invitation(), invitedBy, request)
                .detail("type", "client").detail("clientId", clientId.toString())
                .record();

        return issued.invitation();
    }

    /**
     * The sanctioned project-to-workspace seam, taking primitives only; the calling controller gates on
     * {@code CLIENT_RECORD_MANAGE}. An active member gains the CLIENT role on their membership; a stranger gets an
     * invitation. Either way an email goes, so it spends the invitation budget a staff invitation does — any staff
     * member reaches this door, and the client name they type rides the subject line.
     */
    @Transactional
    public ClientRepresentativeOnboarding onboardClientRepresentative(
            UUID workspaceId, UUID clientId, String clientName, String rawEmail, UUID addedBy,
            HttpServletRequest request) {
        String email = EmailAddressValidator.normalise(rawEmail);
        emailValidator.validateWorkEmail(email);
        rateLimit.checkInvitations(workspaceId, List.of(email), request);

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

        EmailMessage notice = templates.buildRepresentativeAddedEmail(
                email, recipient.getFullName(), adder.getFullName(), workspace.getName(), clientName);
        mailOnceCommitted(() -> emailSender.send(notice));

        audit.event(WorkspaceEventType.MEMBER_ROLE_CHANGED)
                .actor(addedBy).workspace(workspaceId).target("member", member.getId()).from(request)
                .detail("addedRole", WorkspaceRole.CLIENT.name()).detail("clientId", clientId.toString())
                .record();

        return new ClientRepresentativeOnboarding(true, member.getUserId(), null);
    }

    @Transactional(readOnly = true)
    public long countPendingStaff(UUID workspaceId) {
        // Redeemable only: a lapsed invitation is waiting on nobody until an admin resends it.
        return pendingStaffInvitees(workspaceId, clock.instant()).size();
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

    /** Resend rotates the token, so the earlier emailed link dies with it. A refused send is said, never swallowed. */
    public void resend(UUID userId, UUID workspaceId, UUID invitationId, HttpServletRequest request) {
        OutgoingInvitation outgoing = transactions.execute(status -> reissue(userId, workspaceId, invitationId, request));
        if (!deliver(List.of(outgoing), userId, request).isEmpty()) {
            throw ApiException.of(ErrorCode.INVITATION_EMAIL_NOT_SENT);
        }
    }

    private OutgoingInvitation reissue(UUID userId, UUID workspaceId, UUID invitationId, HttpServletRequest request) {
        access.requireAdmin(userId, workspaceId);
        Invitation invitation = requirePendingInvitation(workspaceId, invitationId);
        Instant now = clock.instant();
        // A lapsed invitation stopped counting against seats, so renewing it takes one again.
        if (!invitation.isRedeemable(now)
                && activeMember(workspaceId, invitation.getEmail()).filter(WorkspaceMember::holdsStaffRole).isEmpty()) {
            requireSeatsWithAlso(workspaceId, List.of(EmailAddressValidator.normalise(invitation.getEmail())), now);
        }
        rateLimit.checkInvitations(workspaceId, List.of(invitation.getEmail()), request);

        Workspace workspace = requireWorkspace(workspaceId);
        User inviter = requireUser(userId);
        Role role = invitation.getRole();

        IssuedInvitation issued = issue(workspaceId, null, invitation.getEmail(), role, inviter.getId(),
                now.plus(properties.auth().invitationTtl()));
        recordInvited(issued.invitation(), inviter.getId(), request).detail("role", role.getName()).record();
        return new OutgoingInvitation(issued.invitation().getId(), workspaceId, invitation.getEmail(),
                templates.buildInvitationEmail(invitation.getEmail(), inviter.getFullName(), workspace.getName(),
                        role.getName(), issued.acceptLink()));
    }

    /** Withdraws what an inviter still has outstanding once their approval no longer stands. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void withdrawSentBy(UUID actorId, UUID workspaceId, UUID inviterId, InvitationWithdrawal withdrawal,
                               HttpServletRequest request) {
        invitations.findByWorkspaceIdAndInvitedByAndStatus(workspaceId, inviterId, InvitationStatus.PENDING).stream()
                .filter(invitation -> withdrawal.reachesClientInvitations() || invitation.getClientId() == null)
                .forEach(invitation -> withdraw(invitation, actorId, withdrawal, request));
    }

    /** Cancels a client contact's outstanding invitation; the link dies and the contact row follows. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeClientInvitation(UUID actorId, UUID workspaceId, UUID clientId, UUID invitationId,
                                       HttpServletRequest request) {
        withdraw(requirePendingClientInvitation(workspaceId, clientId, invitationId), actorId,
                InvitationWithdrawal.CANCELLED, request);
    }

    private void withdraw(Invitation invitation, UUID actorId, InvitationWithdrawal withdrawal,
                          HttpServletRequest request) {
        invitation.revoke();
        audit.event(WorkspaceEventType.INVITATION_REVOKED)
                .actor(actorId).workspace(invitation.getWorkspaceId()).target("invitation", invitation.getId())
                .from(request)
                .detail("email", invitation.getEmail()).detail("reason", withdrawal.auditKey())
                .record();
        if (invitation.getClientId() != null) {
            events.publishEvent(new ClientInvitationWithdrawn(
                    invitation.getWorkspaceId(), invitation.getClientId(), invitation.getEmail()));
        }
    }

    /**
     * Resends a client contact's invitation, rotating its token as a staff resend does. Not transactional: the email
     * goes once the new token has committed, and a refused one is said.
     */
    public void resendClientInvitation(UUID actorId, UUID workspaceId, UUID clientId, String clientName,
                                       UUID invitationId, HttpServletRequest request) {
        OutgoingInvitation outgoing = transactions.execute(status -> {
            Invitation invitation = requirePendingClientInvitation(workspaceId, clientId, invitationId);
            rateLimit.checkInvitations(workspaceId, List.of(invitation.getEmail()), request);
            Workspace workspace = requireWorkspace(workspaceId);
            User inviter = requireUser(actorId);
            IssuedInvitation issued = issue(workspaceId, clientId, invitation.getEmail(), invitation.getRole(),
                    actorId, clock.instant().plus(properties.auth().invitationTtl()));
            recordInvited(issued.invitation(), actorId, request)
                    .detail("type", "client").detail("clientId", clientId.toString())
                    .record();
            return new OutgoingInvitation(invitation.getId(), workspaceId, invitation.getEmail(),
                    templates.buildClientInvitationEmail(invitation.getEmail(), inviter.getFullName(),
                            workspace.getName(), clientName, issued.acceptLink()));
        });
        if (!deliver(List.of(outgoing), actorId, request).isEmpty()) {
            throw ApiException.of(ErrorCode.INVITATION_EMAIL_NOT_SENT);
        }
    }

    /**
     * The state of a client record's contacts' invitations, by id; foreign or unknown ids are absent. Whether a link
     * has lapsed is decided here, beside the rule that redeems it.
     */
    @Transactional(readOnly = true)
    public Map<UUID, ClientInvitationState> clientInvitationStates(UUID workspaceId, Collection<UUID> invitationIds) {
        Instant now = clock.instant();
        Map<UUID, ClientInvitationState> states = new HashMap<>();
        if (!invitationIds.isEmpty()) {
            invitations.findByWorkspaceIdAndIdIn(workspaceId, invitationIds).forEach(invitation -> states.put(
                    invitation.getId(),
                    new ClientInvitationState(!invitation.isRedeemable(now), invitation.getEmailFailedAt() != null)));
        }
        return states;
    }

    private Invitation requirePendingClientInvitation(UUID workspaceId, UUID clientId, UUID invitationId) {
        return invitations.findById(invitationId)
                .filter(inv -> inv.getWorkspaceId().equals(workspaceId))
                .filter(inv -> inv.getStatus() == InvitationStatus.PENDING)
                .filter(inv -> clientId.equals(inv.getClientId()))
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private Invitation requirePendingInvitation(UUID workspaceId, UUID invitationId) {
        return invitations.findById(invitationId)
                .filter(inv -> inv.getWorkspaceId().equals(workspaceId))
                .filter(inv -> inv.getStatus() == InvitationStatus.PENDING)
                // A client-rep invitation's id is invisible here, exactly like a foreign workspace's.
                .filter(inv -> inv.getClientId() == null)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /** Room for every staff invitation still redeemable plus these addresses, each counted once. */
    private void requireSeatsWithAlso(UUID workspaceId, Collection<String> normalisedEmails, Instant now) {
        Set<String> pendingAfter = pendingStaffInvitees(workspaceId, now);
        pendingAfter.addAll(normalisedEmails);
        seats.requireRoomFor(workspaceId, pendingAfter.size());
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
