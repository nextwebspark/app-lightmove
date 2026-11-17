package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.CreateExitReasonRequest;
import app.lightmove.api.candidate.dto.CreatePipelineStageRequest;
import app.lightmove.api.candidate.dto.CreatePipelineStatusRequest;
import app.lightmove.api.candidate.dto.ExitReasonCatalogResponse;
import app.lightmove.api.candidate.dto.PipelineCatalogResponse;
import app.lightmove.api.candidate.dto.PipelineRetirePreviewResponse;
import app.lightmove.api.candidate.dto.ReorderPipelineRequest;
import app.lightmove.api.candidate.dto.StatusAutomationResponse;
import app.lightmove.api.candidate.dto.UpdateExitReasonRequest;
import app.lightmove.api.candidate.dto.UpdatePipelineStageRequest;
import app.lightmove.api.candidate.dto.UpdatePipelineStatusRequest;
import app.lightmove.api.candidate.dto.UpdateStatusAutomationRequest;
import app.lightmove.api.candidate.service.ExitReasonService;
import app.lightmove.api.candidate.service.PipelineCatalogService;
import app.lightmove.api.candidate.service.PipelineStageService;
import app.lightmove.api.candidate.service.PipelineStatusService;
import app.lightmove.api.candidate.service.StatusAutomationService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The workspace's pipeline stages and statuses. Reading is any member's, a client seat included: the
 * labels are what every candidate read already carries. Every change is an admin's, under
 * {@code WORKSPACE_MANAGE}, and answers the whole catalog. The automations, the retire previews and the reasons for
 * leaving the process are staff reads: they say how the firm works its candidates, which a client seat has no part in.
 */
@RestController
@RequestMapping("/api/v1/workspace/pipeline")
@RequiredArgsConstructor
public class CandidatePipelineController {

    private final PipelineCatalogService catalogs;
    private final PipelineStageService stages;
    private final PipelineStatusService statuses;
    private final StatusAutomationService automations;
    private final ExitReasonService reasons;

    @GetMapping
    @PreAuthorize("@workspaceAuthorizer.member(principal)")
    public PipelineCatalogResponse catalog(@AuthenticationPrincipal AuthPrincipal principal) {
        return catalogs.catalog(principal.requireWorkspaceId());
    }

    @PostMapping("/stages")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public PipelineCatalogResponse createStage(@AuthenticationPrincipal AuthPrincipal principal,
                                               @Valid @RequestBody CreatePipelineStageRequest request,
                                               HttpServletRequest httpRequest) {
        return stages.createStage(principal.userId(), principal.requireWorkspaceId(), request, httpRequest);
    }

    @PatchMapping("/stages/{stageId}")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public PipelineCatalogResponse updateStage(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable UUID stageId,
                                               @Valid @RequestBody UpdatePipelineStageRequest request,
                                               HttpServletRequest httpRequest) {
        return stages.updateStage(principal.userId(), principal.requireWorkspaceId(), stageId, request,
                httpRequest);
    }

    @PutMapping("/stages/order")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public PipelineCatalogResponse reorderStages(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody ReorderPipelineRequest request,
                                                 HttpServletRequest httpRequest) {
        return stages.reorderStages(principal.userId(), principal.requireWorkspaceId(), request.ids(),
                httpRequest);
    }

    @PutMapping("/stages/{stageId}/statuses/order")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public PipelineCatalogResponse reorderStatuses(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable UUID stageId,
                                                   @Valid @RequestBody ReorderPipelineRequest request,
                                                   HttpServletRequest httpRequest) {
        return statuses.reorderStatuses(principal.userId(), principal.requireWorkspaceId(), stageId,
                request.ids(), httpRequest);
    }

    @PostMapping("/statuses")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public PipelineCatalogResponse createStatus(@AuthenticationPrincipal AuthPrincipal principal,
                                                @Valid @RequestBody CreatePipelineStatusRequest request,
                                                HttpServletRequest httpRequest) {
        return statuses.createStatus(principal.userId(), principal.requireWorkspaceId(), request, httpRequest);
    }

    @PatchMapping("/statuses/{statusId}")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public PipelineCatalogResponse updateStatus(@AuthenticationPrincipal AuthPrincipal principal,
                                                @PathVariable UUID statusId,
                                                @Valid @RequestBody UpdatePipelineStatusRequest request,
                                                HttpServletRequest httpRequest) {
        return statuses.updateStatus(principal.userId(), principal.requireWorkspaceId(), statusId, request,
                httpRequest);
    }

    @GetMapping("/stages/{stageId}/retire-preview")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public PipelineRetirePreviewResponse stageRetirePreview(@AuthenticationPrincipal AuthPrincipal principal,
                                                            @PathVariable UUID stageId) {
        return catalogs.stageRetirePreview(principal.requireWorkspaceId(), stageId);
    }

    @GetMapping("/statuses/{statusId}/retire-preview")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public PipelineRetirePreviewResponse statusRetirePreview(@AuthenticationPrincipal AuthPrincipal principal,
                                                             @PathVariable UUID statusId) {
        return catalogs.statusRetirePreview(principal.requireWorkspaceId(), statusId);
    }

    @GetMapping("/reasons")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public ExitReasonCatalogResponse reasons(@AuthenticationPrincipal AuthPrincipal principal) {
        return reasons.catalog(principal.requireWorkspaceId());
    }

    @PostMapping("/reasons")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public ExitReasonCatalogResponse createReason(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @Valid @RequestBody CreateExitReasonRequest request,
                                                  HttpServletRequest httpRequest) {
        return reasons.create(principal.userId(), principal.requireWorkspaceId(), request, httpRequest);
    }

    @PatchMapping("/reasons/{reasonId}")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public ExitReasonCatalogResponse updateReason(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable UUID reasonId,
                                                  @Valid @RequestBody UpdateExitReasonRequest request,
                                                  HttpServletRequest httpRequest) {
        return reasons.update(principal.userId(), principal.requireWorkspaceId(), reasonId, request, httpRequest);
    }

    @PutMapping("/reasons/order")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public ExitReasonCatalogResponse reorderReasons(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @Valid @RequestBody ReorderPipelineRequest request,
                                                    HttpServletRequest httpRequest) {
        return reasons.reorder(principal.userId(), principal.requireWorkspaceId(), request.ids(), httpRequest);
    }

    @GetMapping("/automations")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public List<StatusAutomationResponse> automations(@AuthenticationPrincipal AuthPrincipal principal) {
        return automations.automationsOf(principal.requireWorkspaceId());
    }

    @PutMapping("/automations/{trigger}")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public List<StatusAutomationResponse> changeAutomation(@AuthenticationPrincipal AuthPrincipal principal,
                                                           @PathVariable String trigger,
                                                           @Valid @RequestBody UpdateStatusAutomationRequest request,
                                                           HttpServletRequest httpRequest) {
        return automations.change(principal.userId(), principal.requireWorkspaceId(), trigger, request, httpRequest);
    }
}
