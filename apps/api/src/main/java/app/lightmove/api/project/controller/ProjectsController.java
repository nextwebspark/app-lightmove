package app.lightmove.api.project.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.project.dto.AttachRepresentativeRequest;
import app.lightmove.api.project.dto.CreateProjectRequest;
import app.lightmove.api.project.dto.InviteRepresentativeRequest;
import app.lightmove.api.project.dto.ProjectActivityResponse;
import app.lightmove.api.project.dto.ProjectResponse;
import app.lightmove.api.project.dto.PutTeamMemberRequest;
import app.lightmove.api.project.dto.UpdateProjectRequest;
import app.lightmove.api.project.service.ClientRepresentativeService;
import app.lightmove.api.project.service.ProjectActivityService;
import app.lightmove.api.project.service.ProjectService;
import app.lightmove.api.project.service.ProjectTeamService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mandates of the caller's workspace, which comes from the principal, never the path. Gated per
 * action; the guard beans re-read the database, since the JWT's roles can be 15 minutes stale.
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectsController {

    private final ProjectService projects;
    private final ProjectTeamService team;
    private final ClientRepresentativeService representatives;
    private final ProjectActivityService activity;

    @GetMapping
    @PreAuthorize("@workspaceAuthorizer.member(principal)")
    public List<ProjectResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return projects.list(principal.userId(), principal.requireWorkspaceId());
    }

    @PostMapping
    @RequireWorkspacePermission(WorkspaceAction.PROJECT_CREATE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@AuthenticationPrincipal AuthPrincipal principal,
                                  @Valid @RequestBody CreateProjectRequest request,
                                  HttpServletRequest httpRequest) {
        ProjectResponse created = projects.create(
                principal.userId(), principal.requireWorkspaceId(), request, httpRequest);
        return created;
    }

    /** Staff only: the lines name the firm's own people, which a client seat must not see. */
    @GetMapping("/{projectId}/activity")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ProjectActivityResponse activity(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "" + ProjectActivityService.DEFAULT_PAGE_SIZE) int limit) {
        return activity.list(principal.requireWorkspaceId(), projectId, before, limit);
    }

    @PatchMapping("/{projectId}")
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    public ProjectResponse update(@AuthenticationPrincipal AuthPrincipal principal,
                                  @PathVariable UUID projectId,
                                  @Valid @RequestBody UpdateProjectRequest request,
                                  HttpServletRequest httpRequest) {
        return projects.update(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest);
    }

    @PutMapping("/{projectId}/members/{memberId}")
    @RequireProjectPermission(ProjectAction.TEAM_MANAGE)
    public ProjectResponse putMember(@AuthenticationPrincipal AuthPrincipal principal,
                                     @PathVariable UUID projectId,
                                     @PathVariable UUID memberId,
                                     @Valid @RequestBody PutTeamMemberRequest request,
                                     HttpServletRequest httpRequest) {
        return team.putMember(
                principal.userId(), principal.requireWorkspaceId(), projectId, memberId,
                request.role(), httpRequest);
    }

    @DeleteMapping("/{projectId}/members/{memberId}")
    @RequireProjectPermission(ProjectAction.TEAM_MANAGE)
    public ProjectResponse removeMember(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable UUID projectId,
                                        @PathVariable UUID memberId,
                                        HttpServletRequest httpRequest) {
        return team.removeMember(
                principal.userId(), principal.requireWorkspaceId(), projectId, memberId, httpRequest);
    }

    /** Map a representative the registry already holds onto this mandate — the lead's decision. */
    @PostMapping("/{projectId}/representatives")
    @RequireProjectPermission(ProjectAction.CLIENT_ACCESS_MANAGE)
    public ProjectResponse attachRepresentative(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody AttachRepresentativeRequest request,
            HttpServletRequest httpRequest) {
        return team.attachRepresentative(
                principal.userId(), principal.requireWorkspaceId(), projectId,
                request.representativeId(), httpRequest);
    }

    /**
     * Gated on both tiers, doing one thing from each: minting a representative is the registry's
     * ({@code CLIENT_RECORD_MANAGE}), giving them this mandate is the lead's ({@code CLIENT_ACCESS_MANAGE}).
     */
    @PostMapping("/{projectId}/representatives/invitations")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'CLIENT_ACCESS_MANAGE') "
            + "and @workspaceAuthorizer.can(principal, 'CLIENT_RECORD_MANAGE')")
    public ProjectResponse inviteRepresentative(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody InviteRepresentativeRequest request,
            HttpServletRequest httpRequest) {
        return representatives.inviteToMandate(
                principal.userId(), principal.requireWorkspaceId(), projectId,
                request.fullName(), request.position(), request.email(), httpRequest);
    }

    @DeleteMapping("/{projectId}/representatives/{representativeId}")
    @RequireProjectPermission(ProjectAction.CLIENT_ACCESS_MANAGE)
    public ProjectResponse detachRepresentative(@AuthenticationPrincipal AuthPrincipal principal,
                                                @PathVariable UUID projectId,
                                                @PathVariable UUID representativeId,
                                                HttpServletRequest httpRequest) {
        return team.detachRepresentative(
                principal.userId(), principal.requireWorkspaceId(), projectId,
                representativeId, httpRequest);
    }
}
