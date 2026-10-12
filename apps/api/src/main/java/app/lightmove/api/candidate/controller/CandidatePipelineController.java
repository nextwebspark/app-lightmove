package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.CreatePipelineStageRequest;
import app.lightmove.api.candidate.dto.CreatePipelineStatusRequest;
import app.lightmove.api.candidate.dto.PipelineCatalogResponse;
import app.lightmove.api.candidate.dto.ReorderPipelineRequest;
import app.lightmove.api.candidate.dto.UpdatePipelineStageRequest;
import app.lightmove.api.candidate.dto.UpdatePipelineStatusRequest;
import app.lightmove.api.candidate.service.CandidatePipelineService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The workspace's pipeline stages and statuses. Reading is any member's, a client seat included: the
 * labels are what every candidate read already carries. Every change is an admin's, under
 * {@code WORKSPACE_MANAGE}, and answers the whole catalog.
 */
@RestController
@RequestMapping("/api/v1/workspace/pipeline")
@RequiredArgsConstructor
public class CandidatePipelineController {

    private final CandidatePipelineService pipeline;

    @GetMapping
    @PreAuthorize("@workspaceAuthorizer.member(principal)")
    public PipelineCatalogResponse catalog(@AuthenticationPrincipal AuthPrincipal principal) {
        return pipeline.catalog(principal.requireWorkspaceId());
    }

    @PostMapping("/stages")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public PipelineCatalogResponse createStage(@AuthenticationPrincipal AuthPrincipal principal,
                                               @Valid @RequestBody CreatePipelineStageRequest request,
                                               HttpServletRequest httpRequest) {
        return pipeline.createStage(principal.userId(), principal.requireWorkspaceId(), request, httpRequest);
    }

    @PatchMapping("/stages/{stageId}")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public PipelineCatalogResponse updateStage(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable UUID stageId,
                                               @Valid @RequestBody UpdatePipelineStageRequest request,
                                               HttpServletRequest httpRequest) {
        return pipeline.updateStage(principal.userId(), principal.requireWorkspaceId(), stageId, request,
                httpRequest);
    }

    @PutMapping("/stages/order")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public PipelineCatalogResponse reorderStages(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody ReorderPipelineRequest request,
                                                 HttpServletRequest httpRequest) {
        return pipeline.reorderStages(principal.userId(), principal.requireWorkspaceId(), request.ids(),
                httpRequest);
    }

    @PutMapping("/stages/{stageId}/statuses/order")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public PipelineCatalogResponse reorderStatuses(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable UUID stageId,
                                                   @Valid @RequestBody ReorderPipelineRequest request,
                                                   HttpServletRequest httpRequest) {
        return pipeline.reorderStatuses(principal.userId(), principal.requireWorkspaceId(), stageId,
                request.ids(), httpRequest);
    }

    @PostMapping("/statuses")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public PipelineCatalogResponse createStatus(@AuthenticationPrincipal AuthPrincipal principal,
                                                @Valid @RequestBody CreatePipelineStatusRequest request,
                                                HttpServletRequest httpRequest) {
        return pipeline.createStatus(principal.userId(), principal.requireWorkspaceId(), request, httpRequest);
    }

    @PatchMapping("/statuses/{statusId}")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public PipelineCatalogResponse updateStatus(@AuthenticationPrincipal AuthPrincipal principal,
                                                @PathVariable UUID statusId,
                                                @Valid @RequestBody UpdatePipelineStatusRequest request,
                                                HttpServletRequest httpRequest) {
        return pipeline.updateStatus(principal.userId(), principal.requireWorkspaceId(), statusId, request,
                httpRequest);
    }
}
