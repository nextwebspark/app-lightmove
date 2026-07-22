package app.lightmove.api.project.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.ProjectRole;
import app.lightmove.api.core.security.rbac.RbacService;
import app.lightmove.api.core.security.rbac.Role;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.project.constant.ProjectHealth;
import app.lightmove.api.project.dto.ProjectDtos.CreateProjectRequest;
import app.lightmove.api.project.dto.ProjectDtos.ProjectResponse;
import app.lightmove.api.project.dto.ProjectDtos.TeamMemberResponse;
import app.lightmove.api.project.dto.ProjectDtos.UpdateProjectRequest;
import app.lightmove.api.project.constant.ClientRepStatus;
import app.lightmove.api.project.model.Client;
import app.lightmove.api.project.model.ClientRepresentative;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.model.ProjectMember;
import app.lightmove.api.project.repository.ClientRepository;
import app.lightmove.api.project.repository.ClientRepresentativeRepository;
import app.lightmove.api.project.repository.ProjectMemberRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.workspace.model.WorkspaceMember;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mandates inside one workspace. Tier gating lives on the controllers as {@code @PreAuthorize}
 * (create/browse are workspace actions; edit and team changes are project actions resolved through
 * the seat's roles). What stays here are the invariants that need loaded state — above all: a project
 * never loses its last ADMIN-role seat. Every load is scoped {@code (id, workspaceId)} with the
 * workspace id taken from the principal, never a request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final ProjectRepository projects;
    private final ProjectMemberRepository seats;
    private final ClientRepository clients;
    private final ClientRepresentativeRepository representatives;
    private final PositionService positionService;
    private final WorkspaceAccess access;
    private final RbacService rbac;
    private final UserRepository users;
    private final AuditService audit;

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
        Assembly assembly = assemblyFor(workspaceId, all);
        return all.stream().map(project -> toResponse(project, assembly)).toList();
    }

    /** The creator's seat holds {ADMIN, LEAD} from birth — they own the mandate and run it, until they delegate. */
    /** The mandates of one client, fully assembled (team, health) — the client drawer and portal read this. */
    @Transactional(readOnly = true)
    public List<ProjectResponse> listForClient(UUID workspaceId, UUID clientId) {
        List<Project> forClient = projects.findByWorkspaceIdAndClientIdOrderByCreatedAtDesc(workspaceId, clientId);
        if (forClient.isEmpty()) {
            return List.of();
        }
        Assembly assembly = assemblyFor(workspaceId, forClient);
        return forClient.stream().map(project -> toResponse(project, assembly)).toList();
    }

    @Transactional
    public ProjectResponse create(UUID userId, UUID workspaceId, CreateProjectRequest request,
                                  HttpServletRequest httpRequest) {
        WorkspaceMember creator = access.requireActiveMember(userId, workspaceId);
        requireClient(request.clientId(), workspaceId);

        Project project = projects.save(Project.create(
                workspaceId, request.clientId(), request.positionTitle(), request.targetDate(), userId));
        seats.save(ProjectMember.of(project.getId(), creator.getId(),
                rbac.projectRoles(EnumSet.of(ProjectRole.ADMIN, ProjectRole.LEAD)), userId));
        // The brief arrives drafted, not blank — seeded from the role-template library.
        positionService.seedFor(project);

        log.info("User {} created project {} in workspace {}", userId, project.getId(), workspaceId);
        audit.event(ProjectEventType.PROJECT_CREATED)
                .actor(userId).workspace(workspaceId).target("project", project.getId()).from(httpRequest)
                .detail("position", project.getPositionTitle())
                .record();

        return toResponse(project, assemblyFor(workspaceId, List.of(project)));
    }

    @Transactional
    public ProjectResponse update(UUID userId, UUID workspaceId, UUID projectId,
                                  UpdateProjectRequest request, HttpServletRequest httpRequest) {
        Project project = requireProject(projectId, workspaceId);

        if (request.targetDate() != null) {
            project.setTargetDate(request.targetDate());
        }

        audit.event(ProjectEventType.PROJECT_UPDATED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .record();

        return toResponse(project, assemblyFor(workspaceId, List.of(project)));
    }

    /**
     * PUT of a seat, replace-set: seats the member with these roles, or replaces the roles they hold.
     * Idempotent — a PUT of the current state changes nothing.
     */
    @Transactional
    public ProjectResponse putMember(UUID userId, UUID workspaceId, UUID projectId, UUID memberId,
                                     Set<ProjectRole> roles, HttpServletRequest httpRequest) {
        Project project = requireProject(projectId, workspaceId);
        // A portal-guest (workspace CLIENT) is never seated, whatever roles are asked for — the
        // requested-role guard below only covers the roles named, not the nature of the member.
        access.requireStaffRow(memberId, workspaceId);

        // Clients are invited through the portal flow (a later phase), never seated by staff.
        if (roles.contains(ProjectRole.CLIENT)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Clients are invited to a project, not seated on the team");
        }

        Set<Role> granted = rbac.projectRoles(roles);
        ProjectMember seat = seats.findByProjectIdAndMemberId(projectId, memberId).orElse(null);

        if (seat == null) {
            seats.save(ProjectMember.of(projectId, memberId, granted, userId));
            auditTeamChange(userId, workspaceId, projectId, memberId, "add", httpRequest);
        } else if (!granted.equals(seat.getRoles())) {
            // A PUT of the current role set changes nothing — skip the guard, the write and the audit
            // event, so the "idempotent" claim holds in side effects too, not just in the response.
            if (holdsAdmin(seat) && !roles.contains(ProjectRole.ADMIN)) {
                requireAnotherProjectAdmin(projectId);
            }
            seat.changeRoles(granted);
            auditTeamChange(userId, workspaceId, projectId, memberId, "roles", httpRequest);
        }

        return toResponse(project, assemblyFor(workspaceId, List.of(project)));
    }

    @Transactional
    public ProjectResponse removeMember(UUID userId, UUID workspaceId, UUID projectId, UUID memberId,
                                        HttpServletRequest httpRequest) {
        Project project = requireProject(projectId, workspaceId);

        ProjectMember seat = seats.findByProjectIdAndMemberId(projectId, memberId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (holdsAdmin(seat)) {
            requireAnotherProjectAdmin(projectId);
        }

        seats.delete(seat);
        auditTeamChange(userId, workspaceId, projectId, memberId, "remove", httpRequest);
        return toResponse(project, assemblyFor(workspaceId, List.of(project)));
    }

    /**
     * Attaches a client representative to a mandate: seats their workspace membership with the read-only
     * CLIENT project role, so they may view this project and no other. Idempotent — re-attaching adds
     * nothing. The representative must have accepted (be ACTIVE, with an account) to have a membership to
     * seat. Gated PROJECT_EDIT at the controller — a lead or admin decides who on the client side sees a
     * mandate.
     */
    @Transactional
    public ProjectResponse attachRepresentative(UUID actorId, UUID workspaceId, UUID projectId,
                                                UUID representativeId, HttpServletRequest httpRequest) {
        Project project = requireProject(projectId, workspaceId);
        WorkspaceMember membership = requireActiveRepresentativeMembership(representativeId, workspaceId);
        Role clientRole = rbac.role(ProjectRole.CLIENT);

        ProjectMember seat = seats.findByProjectIdAndMemberId(projectId, membership.getId()).orElse(null);
        if (seat == null) {
            seats.save(ProjectMember.of(projectId, membership.getId(), Set.of(clientRole), actorId));
            auditTeamChange(actorId, workspaceId, projectId, membership.getId(), "attach-client", httpRequest);
        } else if (seat.getRoles().stream().noneMatch(role -> role.is(ProjectRole.CLIENT))) {
            Set<Role> roles = new HashSet<>(seat.getRoles());
            roles.add(clientRole);
            seat.changeRoles(roles);
            auditTeamChange(actorId, workspaceId, projectId, membership.getId(), "attach-client", httpRequest);
        }

        return toResponse(project, assemblyFor(workspaceId, List.of(project)));
    }

    /**
     * Detaches a representative from a mandate. Drops only the CLIENT role — a dual-role member who also
     * staffs the project keeps their staff seat; the seat is deleted only when nothing remains.
     */
    @Transactional
    public ProjectResponse detachRepresentative(UUID actorId, UUID workspaceId, UUID projectId,
                                                UUID representativeId, HttpServletRequest httpRequest) {
        Project project = requireProject(projectId, workspaceId);
        WorkspaceMember membership = requireActiveRepresentativeMembership(representativeId, workspaceId);

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
            auditTeamChange(actorId, workspaceId, projectId, membership.getId(), "detach-client", httpRequest);
        }

        return toResponse(project, assemblyFor(workspaceId, List.of(project)));
    }

    /** The workspace membership behind an ACTIVE representative — the row a mandate seat references. */
    private WorkspaceMember requireActiveRepresentativeMembership(UUID representativeId, UUID workspaceId) {
        ClientRepresentative representative = representatives
                .findByIdAndWorkspaceId(representativeId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (representative.getStatus() != ClientRepStatus.ACTIVE || representative.getUserId() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "The representative must accept their invitation before joining a mandate");
        }
        return access.requireActiveMember(representative.getUserId(), workspaceId);
    }

    private boolean holdsAdmin(ProjectMember seat) {
        return seat.getRoles().stream().anyMatch(role -> role.is(ProjectRole.ADMIN));
    }

    /** The project-tier mirror of the workspace's last-admin rule. */
    private void requireAnotherProjectAdmin(UUID projectId) {
        if (seats.countByRoleName(projectId, ProjectRole.ADMIN.name()) <= 1) {
            throw ApiException.of(ErrorCode.PROJECT_LAST_ADMIN);
        }
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

    private Assembly assemblyFor(UUID workspaceId, List<Project> forProjects) {
        List<UUID> ids = forProjects.stream().map(Project::getId).toList();
        Map<UUID, List<ProjectMember>> seatsByProject = seats.findByProjectIdIn(ids).stream()
                .collect(Collectors.groupingBy(ProjectMember::getProjectId));

        Map<UUID, WorkspaceMember> memberById = access.activeMembers(workspaceId).stream()
                .collect(Collectors.toMap(WorkspaceMember::getId, Function.identity()));
        Map<UUID, String> nameByUserId = users
                .findAllById(memberById.values().stream().map(WorkspaceMember::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
        Map<UUID, String> clientNames = clients.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .collect(Collectors.toMap(Client::getId, Client::getName));

        return new Assembly(seatsByProject, memberById, nameByUserId, clientNames, LocalDate.now());
    }

    private ProjectResponse toResponse(Project project, Assembly assembly) {
        List<TeamMemberResponse> team = assembly.seatsByProject()
                .getOrDefault(project.getId(), List.of()).stream()
                .flatMap(seat -> {
                    WorkspaceMember member = assembly.memberById().get(seat.getMemberId());
                    if (member == null) {
                        return Stream.<TeamMemberResponse>empty();
                    }
                    return Stream.of(new TeamMemberResponse(
                            member.getId(), member.getUserId(),
                            assembly.nameByUserId().getOrDefault(member.getUserId(), ""),
                            names(member.getRoles(), WorkspaceRole::valueOf),
                            names(seat.getRoles(), ProjectRole::valueOf)));
                })
                .toList();

        return new ProjectResponse(
                project.getId(), project.getClientId(),
                assembly.clientNames().getOrDefault(project.getClientId(), ""),
                project.getPositionTitle(), project.getStage(),
                ProjectHealth.derive(project.getStage(), project.getTargetDate(), assembly.today()),
                project.getTargetDate(), team, 0, 0, project.getCreatedAt());
    }

    private static <E extends Enum<E>> List<E> names(Set<Role> roles, Function<String, E> valueOf) {
        return roles.stream()
                .map(Role::getName)
                .sorted(Comparator.naturalOrder())
                .map(valueOf)
                .toList();
    }

    private record Assembly(Map<UUID, List<ProjectMember>> seatsByProject,
                            Map<UUID, WorkspaceMember> memberById,
                            Map<UUID, String> nameByUserId,
                            Map<UUID, String> clientNames,
                            LocalDate today) {
    }
}
