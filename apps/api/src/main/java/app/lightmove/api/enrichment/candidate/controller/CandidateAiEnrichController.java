package app.lightmove.api.enrichment.candidate.controller;

import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.CandidateScorecardResponse;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.enrichment.candidate.dto.NationalityReadingResponse;
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
 * The drawer's AI: Suggest with AI on the Background section, Assess with AI on the Assessment tab, and the
 * nationality reading the Background section offers. All WORK_EXECUTE — a run spends money, and an assessment ranks
 * a person — so a client seat sees none of it.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/candidates/{candidateId}")
@RequiredArgsConstructor
public class CandidateAiEnrichController {

    private final CandidateAiEnrichService aiEnrich;
    private final CandidateService candidates;

    @PostMapping("/background/ai")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public CandidateResponse suggestBackground(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable UUID projectId,
                                               @PathVariable UUID candidateId,
                                               HttpServletRequest httpRequest) {
        return aiEnrich.suggestBackground(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId,
                httpRequest);
    }

    @PostMapping("/assessment/ai")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public CandidateScorecardResponse assess(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable UUID projectId,
                                             @PathVariable UUID candidateId,
                                             HttpServletRequest httpRequest) {
        return aiEnrich.assess(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId,
                httpRequest);
    }

    @GetMapping("/nationality-reading")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ResponseEntity<NationalityReadingResponse> nationalityReading(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID candidateId) {
        return candidates.nationalityReadingOf(principal.requireWorkspaceId(), projectId, candidateId)
                .map(NationalityReadingResponse::of)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
