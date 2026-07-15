package app.lightmove.api.project.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.service.CurrentUser;
import app.lightmove.api.project.dto.ProjectDtos.CreateProjectRequest;
import app.lightmove.api.project.dto.ProjectDtos.ProjectResponse;
import app.lightmove.api.project.dto.ProjectDtos.UpdateProjectRequest;
import app.lightmove.api.project.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mandates of the caller's workspace. The workspace comes from the principal, never the path. */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectsController {

    private final ProjectService projects;

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> list() {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(projects.list(principal.userId(), principal.requireWorkspaceId()));
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request,
                                                  HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        ProjectResponse created = projects.create(
                principal.userId(), principal.requireWorkspaceId(), request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> update(@PathVariable UUID projectId,
                                                  @Valid @RequestBody UpdateProjectRequest request,
                                                  HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(projects.update(
                principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest));
    }

    @PutMapping("/{projectId}/members/{memberId}")
    public ResponseEntity<ProjectResponse> addMember(@PathVariable UUID projectId,
                                                     @PathVariable UUID memberId,
                                                     HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(projects.addMember(
                principal.userId(), principal.requireWorkspaceId(), projectId, memberId, httpRequest));
    }

    @DeleteMapping("/{projectId}/members/{memberId}")
    public ResponseEntity<ProjectResponse> removeMember(@PathVariable UUID projectId,
                                                        @PathVariable UUID memberId,
                                                        HttpServletRequest httpRequest) {
        AuthPrincipal principal = CurrentUser.require();
        return ResponseEntity.ok(projects.removeMember(
                principal.userId(), principal.requireWorkspaceId(), projectId, memberId, httpRequest));
    }
}
