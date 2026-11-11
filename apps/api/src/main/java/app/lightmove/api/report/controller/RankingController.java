package app.lightmove.api.report.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.report.dto.RankingResponse;
import app.lightmove.api.report.dto.RankingScenarioDto;
import app.lightmove.api.report.dto.SaveRankingScenarioRequest;
import app.lightmove.api.report.service.RankingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The report's Ranking chapter. {@code WORK_EXECUTE} both ways: it ranks people on their assessments, which a client
 * seat never reads.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/report/ranking")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService ranking;

    @GetMapping
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public RankingResponse read(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId) {
        return ranking.read(principal.requireWorkspaceId(), projectId);
    }

    /** Answers the saved scenario, or 204 when the one sent is the brief's own weighting and nothing is kept. */
    @PutMapping("/scenario")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ResponseEntity<RankingScenarioDto> saveScenario(@AuthenticationPrincipal AuthPrincipal principal,
                                                           @PathVariable UUID projectId,
                                                           @Valid @RequestBody SaveRankingScenarioRequest request,
                                                           HttpServletRequest httpRequest) {
        RankingScenarioDto saved = ranking.save(principal.userId(), principal.requireWorkspaceId(), projectId,
                request, httpRequest);
        return saved == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(saved);
    }
}
