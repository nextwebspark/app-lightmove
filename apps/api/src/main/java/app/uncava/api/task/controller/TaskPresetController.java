package app.uncava.api.task.controller;

import app.uncava.api.core.security.model.AuthPrincipal;
import app.uncava.api.core.security.rbac.RequireWorkspacePermission;
import app.uncava.api.core.security.rbac.WorkspaceAction;
import app.uncava.api.task.dto.CreateTaskPresetRequest;
import app.uncava.api.task.dto.ReorderTaskPresetsRequest;
import app.uncava.api.task.dto.TaskPresetResponse;
import app.uncava.api.task.dto.TaskPresetSuggestionResponse;
import app.uncava.api.task.dto.TaskRulesDto;
import app.uncava.api.task.dto.UpdateTaskPresetRequest;
import app.uncava.api.task.service.TaskPresetService;
import app.uncava.api.task.service.TaskRuleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The workspace's task presets and automatic-task rules. Every staff member reads them — the modal offers the
 * presets, the Settings page shows the rules; changing either is {@code WORKSPACE_MANAGE}'s.
 */
@RestController
@RequiredArgsConstructor
public class TaskPresetController {

    private final TaskPresetService presets;
    private final TaskRuleService rules;

    @GetMapping("/api/v1/task-presets")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public List<TaskPresetResponse> catalog(@AuthenticationPrincipal AuthPrincipal principal) {
        return presets.catalog(principal.requireWorkspaceId());
    }

    @GetMapping("/api/v1/task-presets/suggestions")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public List<TaskPresetSuggestionResponse> suggestions(@AuthenticationPrincipal AuthPrincipal principal) {
        return presets.suggestions(principal.requireWorkspaceId());
    }

    @PostMapping("/api/v1/task-presets")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public TaskPresetResponse create(@AuthenticationPrincipal AuthPrincipal principal,
                                     @Valid @RequestBody CreateTaskPresetRequest request,
                                     HttpServletRequest httpRequest) {
        return presets.create(principal.userId(), principal.requireWorkspaceId(), request, httpRequest);
    }

    @PatchMapping("/api/v1/task-presets/{presetId}")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public TaskPresetResponse update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID presetId,
                                     @Valid @RequestBody UpdateTaskPresetRequest request,
                                     HttpServletRequest httpRequest) {
        return presets.update(principal.userId(), principal.requireWorkspaceId(), presetId, request, httpRequest);
    }

    @PutMapping("/api/v1/task-presets/order")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public List<TaskPresetResponse> reorder(@AuthenticationPrincipal AuthPrincipal principal,
                                            @Valid @RequestBody ReorderTaskPresetsRequest request,
                                            HttpServletRequest httpRequest) {
        return presets.reorder(principal.userId(), principal.requireWorkspaceId(), request.presetIds(), httpRequest);
    }

    @GetMapping("/api/v1/workspace/task-rules")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public TaskRulesDto rules(@AuthenticationPrincipal AuthPrincipal principal) {
        return rules.rulesOf(principal.requireWorkspaceId());
    }

    @PutMapping("/api/v1/workspace/task-rules")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public TaskRulesDto changeRules(@AuthenticationPrincipal AuthPrincipal principal,
                                    @Valid @RequestBody TaskRulesDto request, HttpServletRequest httpRequest) {
        return rules.change(principal.userId(), principal.requireWorkspaceId(), request, httpRequest);
    }
}
