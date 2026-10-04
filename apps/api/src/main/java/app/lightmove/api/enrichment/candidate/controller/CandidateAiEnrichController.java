package app.lightmove.api.enrichment.candidate.controller;

import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.enrichment.candidate.dto.CandidateAiAssessmentResponse;
import app.lightmove.api.enrichment.candidate.service.CandidateAiEnrichService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The drawer's AI deep enrich button and the assessment it produces. Both are WORK_EXECUTE: the run
 * spends money and writes the row, and the assessment ranks a person — a client seat sees neither.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/candidates/{candidateId}")
@RequiredArgsConstructor
public class CandidateAiEnrichController {

    private final CandidateAiEnrichService aiEnrich;
    private final CandidateService candidates;

    @PostMapping("/ai-enrich")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ResponseEntity<Void> enrich(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable UUID projectId,
                                       @PathVariable UUID candidateId,
                                       HttpServletRequest httpRequest) {
        aiEnrich.request(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId, httpRequest);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/ai-assessment")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ResponseEntity<CandidateAiAssessmentResponse> assessment(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID candidateId) {
        return candidates.aiAssessmentOf(principal.requireWorkspaceId(), projectId, candidateId)
                .map(CandidateAiAssessmentResponse::of)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
