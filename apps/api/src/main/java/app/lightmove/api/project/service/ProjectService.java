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
import app.lightmove.api.position.service.PositionService;
import app.lightmove.api.project.constant.ProjectHealth;
import app.lightmove.api.project.dto.AttachedRepresentativeResponse;
import app.lightmove.api.project.dto.CreateProjectRequest;
import app.lightmove.api.project.dto.ProjectResponse;
import app.lightmove.api.project.dto.TeamMemberResponse;
import app.lightmove.api.project.dto.UpdateProjectRequest;
import app.lightmove.api.project.constant.ClientRepStatus;
import app.lightmove.api.project.model.Client;
import app.lightmove.api.project.model.ClientRepresentative;
import app.lightmove.api.project.model.PendingRepresentativeAttachment;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.model.ProjectMember;
import app.lightmove.api.project.model.ProjectTimeline;
import app.lightmove.api.project.repository.ClientRepository;
import app.lightmove.api.project.repository.ClientRepresentativeRepository;
import app.lightmove.api.project.repository.PendingRepresentativeAttachmentRepository;
import app.lightmove.api.project.repository.ProjectMemberRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.workspace.model.WorkspaceMember;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * Mandates inside one workspace and their assembled responses; who sits on one is
 * {@link ProjectTeamService}'s. Every load is scoped {@code (id, workspaceId)} with the workspace id
 * taken from the principal, never a request.
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
    private final ProjectCompanyCounter companyCounter;
    private final ProjectCandidateCounter candidateCounter;
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

    /** The mandates of one client, fully assembled (team, health) — the client drawer reads this. */
    @Transactional(readOnly = true)
    public List<ProjectResponse> listForClient(UUID workspaceId, UUID clientId) {
        List<Project> forClient = projects.findByWorkspaceIdAndClientIdOrderByCreatedAtDesc(workspaceId, clientId);
        if (forClient.isEmpty()) {
            return List.of();
        }
        Assembly assembly = assemblyFor(workspaceId, forClient);
        return forClient.stream().map(project -> toResponse(project, assembly)).toList();
    }

    /** One mandate's seats, named — the report's researcher breakdown reads who staffs it. */
    @Transactional(readOnly = true)
    public List<TeamMemberResponse> teamOf(UUID workspaceId, UUID projectId) {
        Project project = projects.requireInWorkspace(projectId, workspaceId);
        return responseFor(workspaceId, project).team();
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

        ProjectTimeline timeline = ProjectTimeline.resolve(request.projectType(), request.startDate(),
                request.deliveryDate(), request.mappingTargetDate());

        Project project = projects.save(Project.create(workspaceId, request.clientId(),
                request.positionTitle(), request.targetDate(), request.projectType(), timeline, userId));
        seats.save(ProjectMember.of(project.getId(), creator.getId(),
                rbac.projectRoles(EnumSet.of(ProjectRole.LEAD)), userId));
        // Seeded from the role-template library, and handed the facts it needs rather than the
        // mandate itself: the project row is this package's.
        positionService.seedFor(workspaceId, project.getId(), project.getPositionTitle(),
                client.getHqCountry());

        log.info("User {} created project {} in workspace {}", userId, project.getId(), workspaceId);
        audit.projectEvent(ProjectEventType.PROJECT_CREATED, userId, workspaceId, project.getId(), httpRequest)
                .detail("position", project.getPositionTitle())
                .detail("type", project.getProjectType().name())
                .record();

        return responseFor(workspaceId, project);
    }

    @Transactional
    public ProjectResponse update(UUID userId, UUID workspaceId, UUID projectId,
                                  UpdateProjectRequest request, HttpServletRequest httpRequest) {
        Project project = projects.requireInWorkspace(projectId, workspaceId);

        if (request.targetDate() != null) {
            project.setTargetDate(request.targetDate());
        }

        audit.projectEvent(ProjectEventType.PROJECT_UPDATED, userId, workspaceId, projectId, httpRequest)
                .record();

        return responseFor(workspaceId, project);
    }

    private Client requireClient(UUID clientId, UUID workspaceId) {
        return clients.findByIdAndWorkspaceId(clientId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /** One mandate, fully assembled — what every write on it answers with. */
    public ProjectResponse responseFor(UUID workspaceId, Project project) {
        return toResponse(project, assemblyFor(workspaceId, List.of(project)));
    }

    private Assembly assemblyFor(UUID workspaceId, List<Project> forProjects) {
        List<UUID> ids = forProjects.stream().map(Project::getId).toList();
        Map<UUID, List<ProjectMember>> seatsByProject = seats.findByProjectIdIn(ids).stream()
                .collect(Collectors.groupingBy(ProjectMember::getProjectId));

        Map<UUID, WorkspaceMember> memberById = access.activeMembers(workspaceId).stream()
                .collect(Collectors.toMap(WorkspaceMember::getId, Function.identity()));
        Map<UUID, User> userById = users
                .findAllById(memberById.values().stream().map(WorkspaceMember::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, Client> clientById = clients.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .collect(Collectors.toMap(Client::getId, Function.identity()));

        List<UUID> clientIds = forProjects.stream().map(Project::getClientId).distinct().toList();
        Map<UUID, List<ClientRepresentative>> repsByClientId = representatives
                .findByWorkspaceIdAndClientIdIn(workspaceId, clientIds).stream()
                .collect(Collectors.groupingBy(ClientRepresentative::getClientId));
        Map<UUID, Set<UUID>> pendingRepIdsByProjectId = pendingAttachments.findByProjectIdIn(ids).stream()
                .collect(Collectors.groupingBy(PendingRepresentativeAttachment::getProjectId,
                        Collectors.mapping(PendingRepresentativeAttachment::getRepresentativeId,
                                Collectors.toSet())));

        return new Assembly(seatsByProject, memberById, userById, clientById,
                repsByClientId, pendingRepIdsByProjectId, companyCounter.countByProject(ids),
                candidateCounter.countByProject(ids), candidateCounter.countMappedByProject(ids),
                candidateCounter.countEngagedByProject(ids),
                candidateCounter.countMappedCompaniesByProject(ids), LocalDate.now());
    }

    private ProjectResponse toResponse(Project project, Assembly assembly) {
        List<TeamMemberResponse> team = assembly.seatsByProject()
                .getOrDefault(project.getId(), List.of()).stream()
                .flatMap(seat -> {
                    WorkspaceMember member = assembly.memberById().get(seat.getMemberId());
                    if (member == null) {
                        return Stream.<TeamMemberResponse>empty();
                    }
                    User user = assembly.userById().get(member.getUserId());
                    return Stream.of(new TeamMemberResponse(
                            member.getId(), member.getUserId(),
                            user == null ? "" : user.getFullName(),
                            user == null ? null : user.getAvatarUrl(),
                            names(member.getRoles(), WorkspaceRole::valueOf),
                            names(seat.getRoles(), ProjectRole::valueOf)));
                })
                // Sorted here, not in the query: the seat rows come back in whatever order the join
                // produced, so the Team & access table would otherwise reshuffle between fetches.
                .sorted(Comparator.comparing(TeamMemberResponse::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<AttachedRepresentativeResponse> attachedRepresentatives = attachedRepresentativesOf(project, assembly);

        Client client = assembly.clientById().get(project.getClientId());
        return new ProjectResponse(
                project.getId(), project.getClientId(),
                client == null ? "" : client.getName(),
                client == null ? null : client.getLogoUrl(),
                project.getPositionTitle(), project.getStage(),
                ProjectHealth.derive(project.getStage(), project.deadline(), assembly.today()),
                project.getTargetDate(), project.getProjectType(), project.getStartDate(),
                project.getDeliveryDate(), project.getMappingTargetDate(), team, attachedRepresentatives,
                assembly.companyCountByProject().getOrDefault(project.getId(), 0L),
                assembly.candidateCountByProject().getOrDefault(project.getId(), 0L),
                assembly.mappedCandidateCountByProject().getOrDefault(project.getId(), 0L),
                assembly.engagedCountByProject().getOrDefault(project.getId(), 0L),
                assembly.mappedCompanyCountByProject().getOrDefault(project.getId(), 0L),
                project.getCreatedAt());
    }

    private List<AttachedRepresentativeResponse> attachedRepresentativesOf(Project project, Assembly assembly) {
        // The client-side contacts on this mandate. Seated wins over a stale pending row, and the
        // reported status is the attachment's ("Active" vs invitation still out), not the registry's.
        //
        // Which means a REVOKED representative whose CLIENT seat was never dropped would still read
        // "Active" here. Nothing revokes today, so nothing is wrong yet — but whoever adds that flow
        // must drop the CLIENT seat and any pending row with it, not merely flip the registry status.
        Set<UUID> clientSeatUserIds = assembly.seatsByProject()
                .getOrDefault(project.getId(), List.of()).stream()
                .filter(seat -> seat.getRoles().stream().anyMatch(role -> role.is(ProjectRole.CLIENT)))
                .map(seat -> assembly.memberById().get(seat.getMemberId()))
                .filter(Objects::nonNull)
                .map(WorkspaceMember::getUserId)
                .collect(Collectors.toSet());
        Set<UUID> pendingRepIds = assembly.pendingRepIdsByProjectId()
                .getOrDefault(project.getId(), Set.of());
        return assembly.repsByClientId()
                .getOrDefault(project.getClientId(), List.of()).stream()
                .filter(rep -> (rep.getUserId() != null && clientSeatUserIds.contains(rep.getUserId()))
                        || pendingRepIds.contains(rep.getId()))
                .sorted(Comparator.comparing(ClientRepresentative::getCreatedAt))
                .map(rep -> {
                    boolean seated = rep.getUserId() != null
                            && clientSeatUserIds.contains(rep.getUserId());
                    return new AttachedRepresentativeResponse(
                            rep.getId(), rep.getFullName(), rep.getPosition(), rep.getEmail(),
                            seated ? ClientRepStatus.ACTIVE : ClientRepStatus.INVITED);
                })
                .toList();
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
                            Map<UUID, User> userById,
                            Map<UUID, Client> clientById,
                            Map<UUID, List<ClientRepresentative>> repsByClientId,
                            Map<UUID, Set<UUID>> pendingRepIdsByProjectId,
                            Map<UUID, Long> companyCountByProject,
                            Map<UUID, Long> candidateCountByProject,
                            Map<UUID, Long> mappedCandidateCountByProject,
                            Map<UUID, Long> engagedCountByProject,
                            Map<UUID, Long> mappedCompanyCountByProject,
                            LocalDate today) {
    }
}
