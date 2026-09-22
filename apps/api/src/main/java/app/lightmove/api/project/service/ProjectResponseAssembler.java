package app.lightmove.api.project.service;

import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.ProjectRole;
import app.lightmove.api.core.security.rbac.Role;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.project.constant.ClientRepStatus;
import app.lightmove.api.project.constant.ProjectHealth;
import app.lightmove.api.project.dto.AttachedRepresentativeResponse;
import app.lightmove.api.project.dto.MandateProgressDto;
import app.lightmove.api.project.dto.ProjectResponse;
import app.lightmove.api.project.dto.TeamMemberResponse;
import app.lightmove.api.project.model.Client;
import app.lightmove.api.project.model.ClientRepresentative;
import app.lightmove.api.project.model.MandateProgress;
import app.lightmove.api.project.model.PendingRepresentativeAttachment;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.model.ProjectMember;
import app.lightmove.api.project.model.ProjectProgressCounts;
import app.lightmove.api.project.repository.ClientRepository;
import app.lightmove.api.project.repository.ClientRepresentativeRepository;
import app.lightmove.api.project.repository.PendingRepresentativeAttachmentRepository;
import app.lightmove.api.project.repository.ProjectMemberRepository;
import app.lightmove.api.workspace.model.WorkspaceMember;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns mandate rows into the shape the projects screens read: the team, the client contacts on the
 * mandate, the two pipeline counts, and the progress every bar and health pill is drawn from.
 *
 * <p>Separate from {@code ProjectService}, which keeps the invariants that need loaded state. Each
 * of the five reads below is one grouped query over every mandate being assembled, so a list of forty
 * costs the same as a list of one.
 */
@Component
@RequiredArgsConstructor
class ProjectResponseAssembler {

    private final ProjectMemberRepository seats;
    private final ClientRepository clients;
    private final ClientRepresentativeRepository representatives;
    private final PendingRepresentativeAttachmentRepository pendingAttachments;
    private final ProjectCompanyCounter companyCounter;
    private final ProjectCandidateCounter candidateCounter;
    private final ProjectProgressCounter progressCounter;
    private final WorkspaceAccess access;
    private final UserRepository users;

    ProjectResponse assemble(UUID workspaceId, Project project) {
        return assembleAll(workspaceId, List.of(project)).getFirst();
    }

    List<ProjectResponse> assembleAll(UUID workspaceId, List<Project> projects) {
        if (projects.isEmpty()) {
            return List.of();
        }
        Assembly assembly = assemblyFor(workspaceId, projects);
        return projects.stream().map(project -> toResponse(project, assembly)).toList();
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
                candidateCounter.countByProject(ids), progressCounter.countByProject(ids),
                LocalDate.now());
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
        List<AttachedRepresentativeResponse> attachedRepresentatives = assembly.repsByClientId()
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

        MandateProgress progress = new MandateProgress(project.timeline(),
                assembly.progressByProject().getOrDefault(project.getId(), ProjectProgressCounts.NONE),
                assembly.today());

        Client client = assembly.clientById().get(project.getClientId());
        return new ProjectResponse(
                project.getId(), project.getClientId(),
                client == null ? "" : client.getName(),
                client == null ? null : client.getLogoUrl(),
                project.getPositionTitle(), project.getStage(), project.getProjectType(),
                ProjectHealth.derive(project.getStage(), progress),
                project.getStartDate(), project.getMappingTargetDate(),
                project.getShortlistTargetDate(), project.getTargetDate(),
                toProgressDto(progress), team, attachedRepresentatives,
                assembly.companyCountByProject().getOrDefault(project.getId(), 0L),
                assembly.candidateCountByProject().getOrDefault(project.getId(), 0L),
                project.getCreatedAt());
    }

    private static MandateProgressDto toProgressDto(MandateProgress progress) {
        ProjectProgressCounts counts = progress.counts();
        return new MandateProgressDto(
                progress.activePhase(), progress.mappingComplete(),
                progress.mapPercent(), progress.engagePercent(),
                counts.universeTotal(), counts.researched(), counts.candidatesTotal(),
                counts.pastIdentified(), counts.qualified(),
                progress.mappingVelocityPerWeek(),
                progress.governingMilestone(), progress.daysRemaining());
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
                            Map<UUID, ProjectProgressCounts> progressByProject,
                            LocalDate today) {
    }
}
