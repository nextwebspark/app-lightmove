package app.lightmove.api.project.service;

import app.lightmove.api.core.audit.constant.AuditEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.project.constant.ProjectHealth;
import app.lightmove.api.project.constant.ProjectRole;
import app.lightmove.api.project.dto.ProjectDtos.CreateProjectRequest;
import app.lightmove.api.project.dto.ProjectDtos.ProjectResponse;
import app.lightmove.api.project.dto.ProjectDtos.TeamMemberResponse;
import app.lightmove.api.project.dto.ProjectDtos.UpdateProjectRequest;
import app.lightmove.api.project.model.Client;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.model.ProjectMember;
import app.lightmove.api.project.repository.ClientRepository;
import app.lightmove.api.project.repository.ProjectMemberRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.service.WorkspaceAccess;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mandates inside one workspace. Any active member may create projects and adjust teams — consultants
 * run their own mandates; workspace governance stays with admins. Every load is scoped
 * {@code (id, workspaceId)} with the workspace id taken from the principal, never a request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final ProjectRepository projects;
    private final ProjectMemberRepository seats;
    private final ClientRepository clients;
    private final WorkspaceAccess access;
    private final UserRepository users;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(UUID userId, UUID workspaceId) {
        access.requireActiveMember(userId, workspaceId);
        List<Project> all = projects.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        if (all.isEmpty()) {
            return List.of();
        }
        Assembly assembly = assemblyFor(workspaceId, all);
        return all.stream().map(project -> toResponse(project, assembly)).toList();
    }

    @Transactional
    public ProjectResponse create(UUID userId, UUID workspaceId, CreateProjectRequest request,
                                  HttpServletRequest httpRequest) {
        access.requireActiveMember(userId, workspaceId);
        requireClient(request.clientId(), workspaceId);
        access.requireActiveMemberRow(request.leadMemberId(), workspaceId);

        Project project = projects.save(Project.create(
                workspaceId, request.clientId(), request.positionTitle(), request.targetDate(), userId));
        seats.save(ProjectMember.of(project.getId(), request.leadMemberId(), ProjectRole.LEAD, userId));

        log.info("User {} created project {} in workspace {}", userId, project.getId(), workspaceId);
        audit.event(AuditEventType.PROJECT_CREATED)
                .actor(userId).workspace(workspaceId).target("project", project.getId()).from(httpRequest)
                .detail("position", project.getPositionTitle())
                .record();

        return toResponse(project, assemblyFor(workspaceId, List.of(project)));
    }

    /** Reassigning the lead is the escape hatch for removing a member who leads projects. */
    @Transactional
    public ProjectResponse update(UUID userId, UUID workspaceId, UUID projectId,
                                  UpdateProjectRequest request, HttpServletRequest httpRequest) {
        access.requireActiveMember(userId, workspaceId);
        Project project = requireProject(projectId, workspaceId);

        if (request.targetDate() != null) {
            project.setTargetDate(request.targetDate());
        }
        if (request.leadMemberId() != null) {
            changeLead(project, request.leadMemberId(), workspaceId, userId);
        }

        audit.event(AuditEventType.PROJECT_UPDATED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .record();

        return toResponse(project, assemblyFor(workspaceId, List.of(project)));
    }

    /** Idempotent — PUT of an existing seat changes nothing. */
    @Transactional
    public ProjectResponse addMember(UUID userId, UUID workspaceId, UUID projectId, UUID memberId,
                                     HttpServletRequest httpRequest) {
        access.requireActiveMember(userId, workspaceId);
        Project project = requireProject(projectId, workspaceId);
        access.requireActiveMemberRow(memberId, workspaceId);

        if (seats.findByProjectIdAndMemberId(projectId, memberId).isEmpty()) {
            seats.save(ProjectMember.of(projectId, memberId, ProjectRole.MEMBER, userId));
            auditTeamChange(userId, workspaceId, projectId, memberId, "add", httpRequest);
        }
        return toResponse(project, assemblyFor(workspaceId, List.of(project)));
    }

    @Transactional
    public ProjectResponse removeMember(UUID userId, UUID workspaceId, UUID projectId, UUID memberId,
                                        HttpServletRequest httpRequest) {
        access.requireActiveMember(userId, workspaceId);
        Project project = requireProject(projectId, workspaceId);

        ProjectMember seat = seats.findByProjectIdAndMemberId(projectId, memberId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (seat.isLead()) {
            throw ApiException.of(ErrorCode.PROJECT_LEAD_REQUIRED);
        }

        seats.delete(seat);
        auditTeamChange(userId, workspaceId, projectId, memberId, "remove", httpRequest);
        return toResponse(project, assemblyFor(workspaceId, List.of(project)));
    }

    /** Demote-flush-promote, so the one-lead partial unique index never sees two LEAD rows. */
    private void changeLead(Project project, UUID newLeadMemberId, UUID workspaceId, UUID actorId) {
        access.requireActiveMemberRow(newLeadMemberId, workspaceId);

        ProjectMember currentLead = seats.findByProjectIdAndRole(project.getId(), ProjectRole.LEAD)
                .orElse(null);
        if (currentLead != null && currentLead.getMemberId().equals(newLeadMemberId)) {
            return;
        }
        if (currentLead != null) {
            currentLead.demote();
            seats.flush();
        }
        seats.findByProjectIdAndMemberId(project.getId(), newLeadMemberId)
                .ifPresentOrElse(
                        ProjectMember::promote,
                        () -> seats.save(ProjectMember.of(
                                project.getId(), newLeadMemberId, ProjectRole.LEAD, actorId)));
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
        audit.event(AuditEventType.PROJECT_TEAM_CHANGED)
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
                            member.getRole(), seat.getRole()));
                })
                .toList();

        return new ProjectResponse(
                project.getId(), project.getClientId(),
                assembly.clientNames().getOrDefault(project.getClientId(), ""),
                project.getPositionTitle(), project.getStage(),
                ProjectHealth.derive(project.getStage(), project.getTargetDate(), assembly.today()),
                project.getTargetDate(), team, 0, 0, project.getCreatedAt());
    }

    private record Assembly(Map<UUID, List<ProjectMember>> seatsByProject,
                            Map<UUID, WorkspaceMember> memberById,
                            Map<UUID, String> nameByUserId,
                            Map<UUID, String> clientNames,
                            LocalDate today) {
    }
}
