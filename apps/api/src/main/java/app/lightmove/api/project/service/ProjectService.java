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
import app.lightmove.api.position.service.PositionService;
import app.lightmove.api.project.dto.CreateProjectRequest;
import app.lightmove.api.project.dto.ProjectResponse;
import app.lightmove.api.project.dto.UpdateProjectRequest;
import app.lightmove.api.project.constant.ClientRepStatus;
import app.lightmove.api.project.model.Client;
import app.lightmove.api.project.model.ClientRepresentative;
import app.lightmove.api.project.model.PendingRepresentativeAttachment;
import app.lightmove.api.project.model.MandateTimeline;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.model.ProjectFacts;
import app.lightmove.api.project.model.ProjectMember;
import app.lightmove.api.project.repository.ClientRepository;
import app.lightmove.api.project.repository.ClientRepresentativeRepository;
import app.lightmove.api.project.repository.PendingRepresentativeAttachmentRepository;
import app.lightmove.api.project.repository.ProjectMemberRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.workspace.model.WorkspaceMember;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mandates inside one workspace. Tier gating lives on the controllers as {@code @PreAuthorize}
 * (create/browse are workspace actions; edit and team changes are project actions resolved through
 * the seat's roles). What stays here are the invariants that need loaded state — above all: a project
 * never loses its last LEAD-role seat, and a seat never holds more than one staff role. Every load is
 * scoped {@code (id, workspaceId)} with the workspace id taken from the principal, never a request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final ProjectRepository projects;
    private final ProjectMemberRepository seats;
    private final ClientRepository clients;
    private final ClientRepresentativeRepository representatives;
    private final PendingRepresentativeAttachmentRepository pendingAttachments;
    private final PositionService positionService;
    private final ProjectResponseAssembler assembler;
    private final WorkspaceAccess access;
    private final RbacService rbac;
    private final UserRepository users;
    private final AuditService audit;
    private final EmailSender emailSender;
    private final EmailTemplates templates;
    private final LightMoveProperties properties;

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(UUID userId, UUID workspaceId) {
        WorkspaceMember member = access.requireActiveMember(userId, workspaceId);
        List<Project> all = projects.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        if (all.isEmpty()) {
            return List.of();
        }
        // Staff browse every mandate; a pure client sees only the ones they are attached to (seated on).
        if (access.isPureClient(member.getId())) {
            Set<UUID> seated = seats.findByMemberId(member.getId()).stream()
                    .map(ProjectMember::getProjectId).collect(Collectors.toSet());
            all = all.stream().filter(project -> seated.contains(project.getId())).toList();
            if (all.isEmpty()) {
                return List.of();
            }
        }
        return assembler.assembleAll(workspaceId, all);
    }

    /**
     * One mandate, named rather than assembled.
     *
     * <p>Scoped on the workspace by the finder itself, so a mandate of another firm is absent
     * rather than refused — the caller asked whether this workspace has one, and it does not.
     *
     * <p>Exists because a caller outside this feature had no way to learn a mandate's title without
     * {@code list}, which assembles every mandate in the workspace, or reaching into two of these
     * repositories. Widening the public surface is the sanctioned answer to both.
     */
    @Transactional(readOnly = true)
    public Optional<ProjectFacts> factsOf(UUID workspaceId, UUID projectId) {
        return projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .map(project -> new ProjectFacts(project.getId(), project.getPositionTitle(),
                        clients.findByIdAndWorkspaceId(project.getClientId(), workspaceId)
                                .map(Client::getName)
                                .orElse(null),
                        project.getStage(), project.getProjectType(),
                        project.getMappingTargetDate(), project.getShortlistTargetDate()));
    }

    /** The mandates of one client, fully assembled (team, health) — the client drawer reads this. */
    @Transactional(readOnly = true)
    public List<ProjectResponse> listForClient(UUID workspaceId, UUID clientId) {
        List<Project> forClient = projects.findByWorkspaceIdAndClientIdOrderByCreatedAtDesc(workspaceId, clientId);
        if (forClient.isEmpty()) {
            return List.of();
        }
        return assembler.assembleAll(workspaceId, forClient);
    }

    /**
     * The creator is the mandate's LEAD from birth — they own it and run it. Handover is an ordinary
     * seat change: promote a second lead, then demote or remove the first.
     */
    @Transactional
    public ProjectResponse create(UUID userId, UUID workspaceId, CreateProjectRequest request,
                                  HttpServletRequest httpRequest) {
        WorkspaceMember creator = access.requireActiveMember(userId, workspaceId);
        Client client = requireClient(request.clientId(), workspaceId);

        MandateTimeline timeline = MandateTimeline.requested(request.projectType(), request.startDate(),
                request.mappingTargetDate(), request.shortlistTargetDate(), LocalDate.now());
        Project project = projects.save(Project.create(workspaceId, request.clientId(),
                request.positionTitle(), timeline, request.targetDate(), userId));
        seats.save(ProjectMember.of(project.getId(), creator.getId(),
                rbac.projectRoles(EnumSet.of(ProjectRole.LEAD)), userId));
        // Seeded from the role-template library, and handed the facts it needs rather than the
        // mandate itself: the project row is this package's.
        positionService.seedFor(workspaceId, project.getId(), project.getPositionTitle(),
                client.getHqCountry());

        log.info("User {} created project {} in workspace {}", userId, project.getId(), workspaceId);
        audit.event(ProjectEventType.PROJECT_CREATED)
                .actor(userId).workspace(workspaceId).target("project", project.getId()).from(httpRequest)
                .detail("position", project.getPositionTitle())
                .record();

        return assembler.assemble(workspaceId, project);
    }

    @Transactional
    public ProjectResponse update(UUID userId, UUID workspaceId, UUID projectId,
                                  UpdateProjectRequest request, HttpServletRequest httpRequest) {
        Project project = requireProject(projectId, workspaceId);

        if (request.targetDate() != null) {
            project.setTargetDate(request.targetDate());
        }
        project.retime(merged(project, request));

        audit.event(ProjectEventType.PROJECT_UPDATED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .record();

        return assembler.assemble(workspaceId, project);
    }

    /**
     * PUT of a seat: the member holds this one staff role on the mandate afterwards — seated if they
     * had no seat, moved if they did. Idempotent — a PUT of the role they already hold changes nothing.
     */
    @Transactional
    public ProjectResponse putMember(UUID userId, UUID workspaceId, UUID projectId, UUID memberId,
                                     ProjectRole role, HttpServletRequest httpRequest) {
        Project project = requireProject(projectId, workspaceId);
        WorkspaceMember membership = access.requireStaffRow(memberId, workspaceId);

        // Clients are attached via attachRepresentative, never seated here.
        if (role == ProjectRole.CLIENT) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "Clients are invited to a project, not seated on the team");
        }

        ProjectMember seat = seats.findByProjectIdAndMemberId(projectId, memberId).orElse(null);

        if (seat == null) {
            seats.save(ProjectMember.of(projectId, memberId, Set.of(rbac.role(role)), userId));
            auditTeamChange(userId, workspaceId, projectId, memberId, "add", httpRequest);
            notifySeated(userId, project, membership, role, true);
            return assembler.assemble(workspaceId, project);
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

        return assembler.assemble(workspaceId, project);
    }

    @Transactional
    public ProjectResponse removeMember(UUID userId, UUID workspaceId, UUID projectId, UUID memberId,
                                        HttpServletRequest httpRequest) {
        Project project = requireProject(projectId, workspaceId);

        ProjectMember seat = seats.findByProjectIdAndMemberId(projectId, memberId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (holdsLead(seat)) {
            requireAnotherProjectLead(projectId);
        }

        seats.delete(seat);
        auditTeamChange(userId, workspaceId, projectId, memberId, "remove", httpRequest);
        return assembler.assemble(workspaceId, project);
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
        Project project = requireProject(projectId, workspaceId);
        ClientRepresentative representative = requireRepresentativeOfClient(representativeId, project);

        if (representative.getStatus() == ClientRepStatus.REVOKED) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "That representative's access was revoked — re-invite them first");
        }
        // ACTIVE without an account is an invariant break, not a state a caller can act on. Masked as
        // NOT_FOUND: there is no request that would make it true.
        if (representative.getStatus() == ClientRepStatus.ACTIVE && representative.getUserId() == null) {
            log.error("Representative {} is ACTIVE with no bound account", representativeId);
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }

        if (representative.getStatus() == ClientRepStatus.ACTIVE) {
            WorkspaceMember membership =
                    access.requireActiveMember(representative.getUserId(), project.getWorkspaceId());
            if (seatRepresentative(projectId, membership, actorId)) {
                auditTeamChange(actorId, workspaceId, projectId, membership.getId(),
                        "attach-client", httpRequest);
                if (announce) {
                    notifyRepresentativeAttached(actorId, project, representative);
                }
            }
        } else if (representative.getStatus() == ClientRepStatus.INVITED) {
            // Named rather than left to the fall-through, so a status added later fails loudly here.
            if (!pendingAttachments.existsByProjectIdAndRepresentativeId(projectId, representativeId)) {
                pendingAttachments.save(
                        PendingRepresentativeAttachment.of(projectId, representativeId, actorId));
                auditRepresentativeChange(actorId, workspaceId, projectId, representativeId,
                        "attach-client-pending", httpRequest);
            }
        } else {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "That representative cannot be added to a mandate in their current state");
        }

        return assembler.assemble(workspaceId, project);
    }

    /**
     * Detaches a representative from a mandate: cancels any pending attachment, and drops only the
     * CLIENT role from an existing seat — a dual-role member who also staffs the project keeps their
     * staff seat; the seat is deleted only when nothing remains.
     */
    @Transactional
    public ProjectResponse detachRepresentative(UUID actorId, UUID workspaceId, UUID projectId,
                                                UUID representativeId, HttpServletRequest httpRequest) {
        Project project = requireProject(projectId, workspaceId);
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

        return assembler.assemble(workspaceId, project);
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
                        .target("project", attachment.getProjectId())
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
                    "That representative belongs to a different client");
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

    /**
     * The patch's timeline fields folded over the stored ones and held to the same rules a create is.
     * Null means "not supplied", so the Role Brief's PATCH of the target start leaves every milestone
     * exactly as it was.
     */
    private static MandateTimeline merged(Project project, UpdateProjectRequest request) {
        MandateTimeline stored = project.timeline();
        return MandateTimeline.requested(
                request.projectType() == null ? stored.type() : request.projectType(),
                request.startDate() == null ? stored.startDate() : request.startDate(),
                request.mappingTargetDate() == null ? stored.mappingTarget() : request.mappingTargetDate(),
                request.shortlistTargetDate() == null ? stored.shortlistTarget() : request.shortlistTargetDate(),
                LocalDate.now());
    }

    private Project requireProject(UUID projectId, UUID workspaceId) {
        return projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private Client requireClient(UUID clientId, UUID workspaceId) {
        return clients.findByIdAndWorkspaceId(clientId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private void auditTeamChange(UUID actorId, UUID workspaceId, UUID projectId, UUID memberId,
                                 String action, HttpServletRequest request) {
        audit.event(ProjectEventType.PROJECT_TEAM_CHANGED)
                .actor(actorId).workspace(workspaceId).target("project", projectId).from(request)
                .detail("memberId", memberId.toString()).detail("action", action)
                .record();
    }

    /** The pending-attachment counterpart of {@link #auditTeamChange} — there is no member to name yet. */
    private void auditRepresentativeChange(UUID actorId, UUID workspaceId, UUID projectId,
                                           UUID representativeId, String action,
                                           HttpServletRequest request) {
        audit.event(ProjectEventType.PROJECT_TEAM_CHANGED)
                .actor(actorId).workspace(workspaceId).target("project", projectId).from(request)
                .detail("representativeId", representativeId.toString()).detail("action", action)
                .record();
    }

}
