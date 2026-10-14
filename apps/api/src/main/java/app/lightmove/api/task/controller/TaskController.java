package app.lightmove.api.task.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.task.dto.CreateTaskRequest;
import app.lightmove.api.task.dto.MyTasksResponse;
import app.lightmove.api.task.dto.TaskResponse;
import app.lightmove.api.task.dto.TasksResponse;
import app.lightmove.api.task.dto.UpdateTaskRequest;
import app.lightmove.api.task.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tasks on a position's executives. {@code WORK_EXECUTE} throughout, reads included: a task is the team's own
 * working fact and a client seat reads none of it. A task named by id is checked against its own position.
 */
@RestController
@RequiredArgsConstructor
public class TaskController {

    private final TaskService tasks;

    @GetMapping("/api/v1/projects/{projectId}/candidates/{candidateId}/tasks")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public TasksResponse list(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                              @PathVariable UUID candidateId,
                              @RequestParam(defaultValue = "false") boolean allDone) {
        return tasks.listOf(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId, allDone);
    }

    @PostMapping("/api/v1/projects/{projectId}/candidates/{candidateId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public TaskResponse create(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                               @PathVariable UUID candidateId, @Valid @RequestBody CreateTaskRequest request,
                               HttpServletRequest httpRequest) {
        return tasks.create(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId, request,
                httpRequest);
    }

    @PatchMapping("/api/v1/tasks/{taskId}")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public TaskResponse update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID taskId,
                               @Valid @RequestBody UpdateTaskRequest request, HttpServletRequest httpRequest) {
        return tasks.update(principal.userId(), principal.requireWorkspaceId(), taskId, request, httpRequest);
    }

    @DeleteMapping("/api/v1/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public void cancel(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID taskId,
                       HttpServletRequest httpRequest) {
        tasks.cancel(principal.userId(), principal.requireWorkspaceId(), taskId, httpRequest);
    }

    @GetMapping("/api/v1/me/tasks")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public MyTasksResponse mine(@AuthenticationPrincipal AuthPrincipal principal,
                                @RequestParam(required = false) String view,
                                @RequestParam(required = false) String tz) {
        return tasks.mine(principal.userId(), principal.requireWorkspaceId(), view, tz);
    }
}
