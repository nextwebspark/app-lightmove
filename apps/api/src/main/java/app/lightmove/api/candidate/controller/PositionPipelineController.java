package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.PipelineRowResponse;
import app.lightmove.api.candidate.dto.PositionPipelineResponse;
import app.lightmove.api.candidate.dto.RankPipelineRowRequest;
import app.lightmove.api.candidate.dto.SetClientVisibilityRequest;
import app.lightmove.api.candidate.dto.SetNextActionRequest;
import app.lightmove.api.candidate.service.PositionPipelineService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A position's Pipeline page. {@code WORK_EXECUTE} throughout, reads included: the next action, the
 * team's order and the visibility switch are staff working facts a client seat never reads. Moving someone
 * between stages is the ordinary status change on {@code CandidateController}.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@RequiredArgsConstructor
public class PositionPipelineController {

    private final PositionPipelineService pipeline;

    @GetMapping("/pipeline")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public PositionPipelineResponse pipeline(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable UUID projectId,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(required = false) String pipelineStage) {
        return pipeline.pipelineOf(principal.requireWorkspaceId(), projectId, q, pipelineStage);
    }

    @PutMapping("/candidates/{candidateId}/pipeline-rank")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public PipelineRowResponse rank(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                                    @PathVariable UUID candidateId,
                                    @Valid @RequestBody RankPipelineRowRequest request,
                                    HttpServletRequest httpRequest) {
        return pipeline.rank(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId, request,
                httpRequest);
    }

    @PutMapping("/candidates/{candidateId}/next-action")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public PipelineRowResponse nextAction(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                          @Valid @RequestBody SetNextActionRequest request,
                                          HttpServletRequest httpRequest) {
        return pipeline.setNextAction(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId,
                request, httpRequest);
    }

    @PutMapping("/candidates/{candidateId}/client-visibility")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public PipelineRowResponse clientVisibility(@AuthenticationPrincipal AuthPrincipal principal,
                                                @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                                @Valid @RequestBody SetClientVisibilityRequest request,
                                                HttpServletRequest httpRequest) {
        return pipeline.setClientVisibility(principal.userId(), principal.requireWorkspaceId(), projectId,
                candidateId, request.clientVisible(), httpRequest);
    }
}
