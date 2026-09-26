package app.lightmove.api.project.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.ProjectRole;
import app.lightmove.api.core.security.rbac.RbacService;
import app.lightmove.api.core.security.rbac.Role;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.project.dto.ProjectResponse;
import app.lightmove.api.project.model.Client;
import app.lightmove.api.project.model.ClientRepresentative;
import app.lightmove.api.project.model.PendingRepresentativeAttachment;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.model.ProjectMember;
import app.lightmove.api.project.repository.ClientRepository;
import app.lightmove.api.project.repository.ClientRepresentativeRepository;
import app.lightmove.api.project.repository.PendingRepresentativeAttachmentRepository;
import app.lightmove.api.project.repository.ProjectMemberRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.workspace.model.WorkspaceMember;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who sits on a mandate — staff seats and client representatives' read-only CLIENT seats. Holds the seat
 * invariants: a project never loses its last LEAD-role seat, and a seat never holds more than one staff role.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectTeamService {

    private final ProjectRepository projects;
    private final ProjectMemberRepository seats;
    private final ClientRepository clients;
    private final ClientRepresentativeRepository representatives;
    private final PendingRepresentativeAttachmentRepository pendingAttachments;
    private final ProjectService projectService;
    private final WorkspaceAccess access;
    private final RbacService rbac;
    private final UserRepository users;
    private final AuditService audit;
    private final EmailSender emailSender;
    private final EmailTemplates templates;
    private final LightMoveProperties properties;

    /**
     * PUT of a seat: the member holds this one staff role on the mandate afterwards — seated if they
     * had no seat, moved if they did. Idempotent — a PUT of the role they already hold changes nothing.
     */
    @Transactional
    public ProjectResponse putMember(UUID userId, UUID workspaceId, UUID projectId, UUID memberId,
                                     ProjectRole role, HttpServletRequest httpRequest) {
        Project project = projects.requireInWorkspace(projectId, workspaceId);
        WorkspaceMember membership = access.requireStaffRow(memberId, workspaceId);

        // Clients are attached via attachRepresentative, never seated here.
        if (role == ProjectRole.CLIENT) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "Hiring managers are invited to a position, not seated on the team");
        }

        ProjectMember seat = seats.findByProjectIdAndMemberId(projectId, memberId).orElse(null);

        if (seat == null) {
            seats.save(ProjectMember.of(projectId, memberId, Set.of(rbac.role(role)), userId));
            auditTeamChange(userId, workspaceId, projectId, memberId, "add", httpRequest);
            notifySeated(userId, project, membership, role, true);
            return projectService.responseFor(workspaceId, project);
        }

        // A seat carrying only CLIENT belongs to a representative who is now being staffed: they are
        // joining the team, not moving within it, and the notice below says so.
        boolean heldStaffRole = seat.getRoles().stream().anyMatch(held -> !held.is(ProjectRole.CLIENT));

        // The staff role is replaced; a CLIENT role the seat already carries survives, so staffing a
        // client's representative does not revoke the read access they were granted separately.
        Set<Role> granted = new HashSet<>();
        granted.add(rbac.role(role));
        seat.getRoles().stream().filter(existing -> existing.is(ProjectRole.CLIENT)).forEach(granted::add);

        // A PUT of the current role set changes nothing, in side effects as well as in the response.
        if (!granted.equals(seat.getRoles())) {
            if (holdsLead(seat) && role != ProjectRole.LEAD) {
                requireAnotherProjectLead(projectId);
            }
            seat.changeRoles(granted);
            auditTeamChange(userId, workspaceId, projectId, memberId, "roles", httpRequest);
            notifySeated(userId, project, membership, role, !heldStaffRole);
        }

        return projectService.responseFor(workspaceId, project);
    }

    @Transactional
    public ProjectResponse removeMember(UUID userId, UUID workspaceId, UUID projectId, UUID memberId,
                                        HttpServletRequest httpRequest) {
        Project project = projects.requireInWorkspace(projectId, workspaceId);

        ProjectMember seat = seats.findByProjectIdAndMemberId(projectId, memberId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (holdsLead(seat)) {
            requireAnotherProjectLead(projectId);
        }

        seats.delete(seat);
        auditTeamChange(userId, workspaceId, projectId, memberId, "remove", httpRequest);
        return projectService.responseFor(workspaceId, project);
    }

    /**
     * Attaches a client representative to a mandate. An ACTIVE one is seated at once — their
     * membership gains the read-only CLIENT project role, so they may view this project and no other.
     * An INVITED one has no membership to seat yet, so the intent is parked and converted when they
     * accept ({@link #seatAcceptedRepresentative}). Idempotent on both paths.
     */
    @Transactional
    public ProjectResponse attachRepresentative(UUID actorId, UUID workspaceId, UUID projectId,
                                                UUID representativeId, HttpServletRequest httpRequest) {
        return attachRepresentative(actorId, workspaceId, projectId, representativeId, true, httpRequest);
    }

    /**
     * The attach above, with the courtesy notice made optional.
     *
     * @param announce false when the caller has already mailed this person about the same decision —
     *                 the invite-and-attach flow. Two mails for one click reads as a bug.
     */
    @Transactional
    public ProjectResponse attachRepresentative(UUID actorId, UUID workspaceId, UUID projectId,
                                                UUID representativeId, boolean announce,
                                                HttpServletRequest httpRequest) {
        Project project = projects.requireInWorkspace(projectId, workspaceId);
        ClientRepresentative representative = requireRepresentativeOfClient(representativeId, project);

        // Exhaustive, so a status added later fails to compile here rather than falling through.
        switch (representative.getStatus()) {
            case REVOKED -> throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "That representative's access was revoked — re-invite them first");
            case ACTIVE -> {
                // ACTIVE without an account is an invariant break, not a state a caller can act on.
                // Masked as NOT_FOUND: there is no request that would make it true.
                if (representative.getUserId() == null) {
                    log.error("Representative {} is ACTIVE with no bound account", representativeId);
                    throw ApiException.of(ErrorCode.NOT_FOUND);
                }
                WorkspaceMember membership =
                        access.requireActiveMember(representative.getUserId(), project.getWorkspaceId());
                if (seatRepresentative(projectId, membership, actorId)) {
                    auditTeamChange(actorId, workspaceId, projectId, membership.getId(),
                            "attach-client", httpRequest);
                    if (announce) {
                        notifyRepresentativeAttached(actorId, project, representative);
                    }
                }
            }
            case INVITED -> {
                if (!pendingAttachments.existsByProjectIdAndRepresentativeId(projectId, representativeId)) {
                    pendingAttachments.save(
                            PendingRepresentativeAttachment.of(projectId, representativeId, actorId));
                    auditRepresentativeChange(actorId, workspaceId, projectId, representativeId,
                            "attach-client-pending", httpRequest);
                }
            }
            case null -> throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "That representative cannot be added to a mandate in their current state");
        }

        return projectService.responseFor(workspaceId, project);
    }

    /**
     * Detaches a representative from a mandate: cancels any pending attachment, and drops only the
     * CLIENT role from an existing seat — a dual-role member who also staffs the project keeps their
     * staff seat; the seat is deleted only when nothing remains.
     */
    @Transactional
    public ProjectResponse detachRepresentative(UUID actorId, UUID workspaceId, UUID projectId,
                                                UUID representativeId, HttpServletRequest httpRequest) {
        Project project = projects.requireInWorkspace(projectId, workspaceId);
        ClientRepresentative representative = requireRepresentativeOfClient(representativeId, project);

        if (pendingAttachments.deleteByProjectIdAndRepresentativeId(projectId, representativeId) > 0) {
            auditRepresentativeChange(actorId, workspaceId, projectId, representativeId,
                    "detach-client-pending", httpRequest);
        }

        if (representative.getUserId() != null) {
            WorkspaceMember membership =
                    access.requireActiveMember(representative.getUserId(), project.getWorkspaceId());
            ProjectMember seat = seats.findByProjectIdAndMemberId(projectId, membership.getId()).orElse(null);
            if (seat != null && seat.getRoles().stream().anyMatch(role -> role.is(ProjectRole.CLIENT))) {
                Set<Role> remaining = seat.getRoles().stream()
                        .filter(role -> !role.is(ProjectRole.CLIENT))
                        .collect(Collectors.toSet());
                if (remaining.isEmpty()) {
                    seats.delete(seat);
                } else {
                    seat.changeRoles(remaining);
                }
                auditTeamChange(actorId, workspaceId, projectId, membership.getId(),
                        "detach-client", httpRequest);
            }
        }

        return projectService.responseFor(workspaceId, project);
    }

    /**
     * Turns every parked attach intent for a just-activated representative into a real CLIENT seat.
     * MANDATORY: it must join the activating transaction, on both INVITED-to-ACTIVE paths, so the
     * seats and the activation land or roll back as one.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void seatAcceptedRepresentative(ClientRepresentative representative) {
        List<PendingRepresentativeAttachment> pending =
                pendingAttachments.findByRepresentativeId(representative.getId());
        if (pending.isEmpty()) {
            return;
        }
        WorkspaceMember membership = access.requireActiveMember(
                representative.getUserId(), representative.getWorkspaceId());
        for (PendingRepresentativeAttachment attachment : pending) {
            if (seatRepresentative(attachment.getProjectId(), membership, attachment.getCreatedBy())) {
                audit.event(ProjectEventType.PROJECT_TEAM_CHANGED)
                        .actor(representative.getUserId()).workspace(representative.getWorkspaceId())
                        .target(AuditService.PROJECT_TARGET, attachment.getProjectId())
                        .detail("memberId", membership.getId().toString())
                        .detail("action", "attach-client-accepted")
                        .record();
            }
        }
        pendingAttachments.deleteAll(pending);
    }

    /**
     * A courtesy notice to an already-active representative that a mandate was just shared with them —
     * a person with a working login gets no other signal. Only the ACTIVE attach path sends it: an
     * INVITED representative's signal is the portal invitation already in their inbox, and the seat
     * granted on accept is the thing that invitation promised.
     */
    private void notifyRepresentativeAttached(UUID actorId, Project project,
                                              ClientRepresentative representative) {
        String adderName = users.findById(actorId).map(User::getFullName).orElse("A colleague");
        String clientName = clients.findByIdAndWorkspaceId(project.getClientId(), project.getWorkspaceId())
                .map(Client::getName).orElse("your client");
        // Sent inline, and last: mail is the one side effect a rollback cannot undo, so the discipline
        // here — as in ClientRepresentativeService.invite — is to order the code so nothing that can
        // throw comes after it, not to defer the send. Deferring only this one would be worse than
        // useless: the "added as a representative" notice from the same transaction is sent inline too.
        emailSender.send(templates.buildAttachedToMandateEmail(
                representative.getEmail(), representative.getFullName(), adderName, clientName,
                project.getPositionTitle()));
    }

    /**
     * Tells a staff member they were put on a mandate, or that their role on one changed — the only
     * signal either gives, since neither touches their workspace membership and nothing is sent when
     * they next sign in.
     *
     * <p>Never sent to the person who made the change: a lead who seats themselves at project
     * creation, or hands the mandate over by demoting their own seat, does not need telling.
     *
     * <p>Sent inline and last, for {@link #notifyRepresentativeAttached}'s reason — mail is the one
     * side effect a rollback cannot undo, so the ordering is the discipline, not a deferred send.
     *
     * @param firstSeat true when they are joining the mandate, false when they already staffed it and
     *                  only their role moved — the difference between the two notices.
     */
    private void notifySeated(UUID actorId, Project project, WorkspaceMember membership,
                              ProjectRole role, boolean firstSeat) {
        if (actorId.equals(membership.getUserId())) {
            return;
        }
        // A membership always names a user. If that ever stops being true there is nobody left to
        // tell, and a seat change must not fail over the notice it could not send.
        User recipient = users.findById(membership.getUserId()).orElse(null);
        if (recipient == null) {
            return;
        }
        String actorName = users.findById(actorId).map(User::getFullName).orElse("A colleague");
        String clientName = clients.findByIdAndWorkspaceId(project.getClientId(), project.getWorkspaceId())
                .map(Client::getName).orElse("your client");
        String link = "%s/projects/%s".formatted(properties.web().baseUrl(), project.getId());

        emailSender.send(firstSeat
                ? templates.buildAddedToProjectEmail(recipient.getEmail(), recipient.getFullName(),
                        actorName, project.getPositionTitle(), clientName, role.name(), link)
                : templates.buildProjectRoleChangedEmail(recipient.getEmail(), recipient.getFullName(),
                        actorName, project.getPositionTitle(), clientName, role.name(), link));
    }

    /** Seats (or extends) the membership with the CLIENT role. Returns whether anything changed. */
    private boolean seatRepresentative(UUID projectId, WorkspaceMember membership, UUID grantedBy) {
        Role clientRole = rbac.role(ProjectRole.CLIENT);
        ProjectMember seat = seats.findByProjectIdAndMemberId(projectId, membership.getId()).orElse(null);
        if (seat == null) {
            seats.save(ProjectMember.of(projectId, membership.getId(), Set.of(clientRole), grantedBy));
            return true;
        }
        if (seat.getRoles().stream().noneMatch(role -> role.is(ProjectRole.CLIENT))) {
            Set<Role> roles = new HashSet<>(seat.getRoles());
            roles.add(clientRole);
            seat.changeRoles(roles);
            return true;
        }
        return false;
    }

    /**
     * The representative a mandate names, whatever their lifecycle state. Must belong to the project's
     * own client: a rep of one client must never be granted a view of another client's mandate.
     */
    private ClientRepresentative requireRepresentativeOfClient(UUID representativeId, Project project) {
        ClientRepresentative representative = representatives
                .findByIdAndWorkspaceId(representativeId, project.getWorkspaceId())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!representative.getClientId().equals(project.getClientId())) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "That hiring manager belongs to a different business unit");
        }
        return representative;
    }

    private boolean holdsLead(ProjectMember seat) {
        return seat.getRoles().stream().anyMatch(role -> role.is(ProjectRole.LEAD));
    }

    /** The project-tier mirror of the workspace's last-admin rule: a mandate always has someone running it. */
    private void requireAnotherProjectLead(UUID projectId) {
        if (seats.countByRoleName(projectId, ProjectRole.LEAD.name()) <= 1) {
            throw ApiException.of(ErrorCode.PROJECT_LAST_LEAD);
        }
    }

    private void auditTeamChange(UUID actorId, UUID workspaceId, UUID projectId, UUID memberId,
                                 String action, HttpServletRequest request) {
        audit.projectEvent(ProjectEventType.PROJECT_TEAM_CHANGED, actorId, workspaceId, projectId, request)
                .detail("memberId", memberId.toString()).detail("action", action)
                .record();
    }

    /** The pending-attachment counterpart of {@link #auditTeamChange} — there is no member to name yet. */
    private void auditRepresentativeChange(UUID actorId, UUID workspaceId, UUID projectId,
                                           UUID representativeId, String action,
                                           HttpServletRequest request) {
        audit.projectEvent(ProjectEventType.PROJECT_TEAM_CHANGED, actorId, workspaceId, projectId, request)
                .detail("representativeId", representativeId.toString()).detail("action", action)
                .record();
    }
}
