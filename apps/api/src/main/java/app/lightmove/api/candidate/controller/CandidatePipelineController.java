package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.CandidatePipelineResponse;
import app.lightmove.api.candidate.dto.CandidatePipelineStaffResponse;
import app.lightmove.api.candidate.service.CandidatePoolService;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The position's Candidates page. Two reads: the rows, which a client seat on the position reads too,
 * and the staff columns beside them, which it never does.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/candidates/pipeline")
@RequiredArgsConstructor
public class CandidatePipelineController {

    private final CandidateService candidates;
    private final CandidatePoolService pool;

    @GetMapping
    @RequireProjectPermission(ProjectAction.WORK_VIEW)
    public CandidatePipelineResponse list(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable UUID projectId,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        return candidates.pipeline(principal.requireWorkspaceId(), projectId, q, status, page, size);
    }

    @GetMapping("/staff")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public CandidatePipelineStaffResponse staff(@AuthenticationPrincipal AuthPrincipal principal,
                                                @PathVariable UUID projectId,
                                                @RequestParam(required = false) List<UUID> candidateId) {
        return pool.pipelineOverlayOf(principal.requireWorkspaceId(), projectId, candidateId);
    }
}
