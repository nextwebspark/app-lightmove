package app.uncava.api.candidate.controller;

import app.uncava.api.candidate.dto.CandidateScorecardResponse;
import app.uncava.api.candidate.dto.SaveScorecardRequest;
import app.uncava.api.candidate.service.CandidateScorecardService;
import app.uncava.api.core.security.model.AuthPrincipal;
import app.uncava.api.core.security.rbac.ProjectAction;
import app.uncava.api.core.security.rbac.RequireProjectPermission;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * The drawer's Assessment tab: a consultant's scores and report on one executive. WORK_EXECUTE both ways — it
 * ranks a person, so a client seat neither reads nor writes it.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/candidates/{candidateId}/assessment")
@RequiredArgsConstructor
public class CandidateScorecardController {

    private final CandidateScorecardService scorecards;

    @GetMapping
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public CandidateScorecardResponse read(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable UUID projectId,
                                           @PathVariable UUID candidateId) {
        return scorecards.read(principal.requireWorkspaceId(), projectId, candidateId);
    }

    @PutMapping
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public CandidateScorecardResponse save(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable UUID projectId,
                                           @PathVariable UUID candidateId,
                                           @Valid @RequestBody SaveScorecardRequest request,
                                           HttpServletRequest httpRequest) {
        return scorecards.save(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId, request,
                httpRequest);
    }
}
