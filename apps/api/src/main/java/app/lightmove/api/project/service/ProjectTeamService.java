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
 * Who sits on a mandate — staff seats and representatives' read-only CLIENT seats. A project never
 * loses its last LEAD, and a seat never holds more than one staff role.
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

    /** Seats the member with this one staff role, or moves them to it. Idempotent. */
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

        // A CLIENT-only seat being staffed is joining the team, not moving within it.
        boolean heldStaffRole = seat.getRoles().stream().anyMatch(held -> !held.is(ProjectRole.CLIENT));

        // The staff role is replaced; a CLIENT role survives, being a separately granted access.
        Set<Role> granted = new HashSet<>();
        granted.add(rbac.role(role));
        seat.getRoles().stream().filter(existing -> existing.is(ProjectRole.CLIENT)).forEach(granted::add);

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
     * An ACTIVE representative gains the read-only CLIENT seat at once; an INVITED one's intent is
     * parked until they accept ({@link #seatAcceptedRepresentative}). Idempotent.
     */
    @Transactional
    public ProjectResponse attachRepresentative(UUID actorId, UUID workspaceId, UUID projectId,
                                                UUID representativeId, HttpServletRequest httpRequest) {
        return attachRepresentative(actorId, workspaceId, projectId, representativeId, true, httpRequest);
    }

    /** @param announce false when the caller already mailed this person (invite-and-attach). */
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
                // An invariant break no request can fix, masked as NOT_FOUND.
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

    /** Drops only the CLIENT role: a dual-role member keeps their staff seat. */
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
     * Seats a just-activated representative's parked attachments. MANDATORY: joins the activating
     * transaction on both INVITED-to-ACTIVE paths, so seats and activation commit or roll back as one.
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

    /** ACTIVE path only: an INVITED representative's signal is the invitation already in their inbox. */
    private void notifyRepresentativeAttached(UUID actorId, Project project,
                                              ClientRepresentative representative) {
        String adderName = users.findById(actorId).map(User::getFullName).orElse("A colleague");
        String clientName = clients.findByIdAndWorkspaceId(project.getClientId(), project.getWorkspaceId())
                .map(Client::getName).orElse("your client");
        // Sent inline and last: a rollback cannot unsend mail, so nothing that can throw may follow it.
        emailSender.send(templates.buildAttachedToMandateEmail(
                representative.getEmail(), representative.getFullName(), adderName, clientName,
                project.getPositionTitle()));
    }

    /**
     * Never sent to the actor themselves; inline and last, as in {@link #notifyRepresentativeAttached}.
     *
     * @param firstSeat true when joining the mandate, false when only their role moved
     */
    private void notifySeated(UUID actorId, Project project, WorkspaceMember membership,
                              ProjectRole role, boolean firstSeat) {
        if (actorId.equals(membership.getUserId())) {
            return;
        }
        // A seat change must not fail over a notice it could not send.
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

    /** Must belong to the project's own client: a rep must never see another client's mandate. */
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

    /** The project-tier mirror of the workspace's last-admin rule. */
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

    private void auditRepresentativeChange(UUID actorId, UUID workspaceId, UUID projectId,
                                           UUID representativeId, String action,
                                           HttpServletRequest request) {
        audit.projectEvent(ProjectEventType.PROJECT_TEAM_CHANGED, actorId, workspaceId, projectId, request)
                .detail("representativeId", representativeId.toString()).detail("action", action)
                .record();
    }
}
