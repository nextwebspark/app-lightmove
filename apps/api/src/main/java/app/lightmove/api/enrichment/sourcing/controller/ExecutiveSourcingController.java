package app.lightmove.api.enrichment.sourcing.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.enrichment.sourcing.dto.AddSuggestionsRequest;
import app.lightmove.api.enrichment.sourcing.dto.AddSuggestionsResponse;
import app.lightmove.api.enrichment.sourcing.dto.ExecutiveSourcingRunResponse;
import app.lightmove.api.enrichment.sourcing.dto.ExecutiveSuggestionsResponse;
import app.lightmove.api.enrichment.sourcing.dto.SourcingCountsRequest;
import app.lightmove.api.enrichment.sourcing.dto.SourcingCountsResponse;
import app.lightmove.api.enrichment.sourcing.dto.SourcingCriteriaDto;
import app.lightmove.api.enrichment.sourcing.dto.SourcingCriteriaResponse;
import app.lightmove.api.enrichment.sourcing.dto.StartExecutiveSourcingRequest;
import app.lightmove.api.enrichment.sourcing.dto.SuggestSourcingCriteriaRequest;
import app.lightmove.api.enrichment.sourcing.service.ExecutiveSourcingService;
import app.lightmove.api.enrichment.sourcing.service.SourcingCounts;
import app.lightmove.api.enrichment.sourcing.service.SourcingCriteriaService;
import app.lightmove.api.enrichment.sourcing.service.SuggestionFiling;
import app.lightmove.api.enrichment.sourcing.service.SuggestionReader;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Find executives: the position's saved search, its free counts, a search over some companies and its stop, what
 * each company's latest search found, and Add. All WORK_EXECUTE — a search spends money, Add writes rows, and the
 * results rank people, which a client seat never sees.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/executive-sourcing")
@RequiredArgsConstructor
public class ExecutiveSourcingController {

    private final ExecutiveSourcingService sourcing;
    private final SourcingCriteriaService criteria;
    private final SourcingCounts counts;
    private final SuggestionReader suggestions;
    private final SuggestionFiling filing;

    @GetMapping("/criteria")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ResponseEntity<SourcingCriteriaResponse> criteria(@AuthenticationPrincipal AuthPrincipal principal,
                                                             @PathVariable UUID projectId) {
        return criteria.get(principal.requireWorkspaceId(), projectId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/criteria")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public SourcingCriteriaResponse saveCriteria(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @PathVariable UUID projectId,
                                                 @Valid @RequestBody SourcingCriteriaDto edited) {
        return criteria.save(principal.userId(), principal.requireWorkspaceId(), projectId, edited.toCriteria());
    }

    @PostMapping("/criteria/suggest")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public SourcingCriteriaResponse suggestCriteria(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @PathVariable UUID projectId,
                                                    @Valid @RequestBody SuggestSourcingCriteriaRequest request) {
        return criteria.suggest(principal.userId(), principal.requireWorkspaceId(), projectId,
                request.triageCompanyIds());
    }

    @PostMapping("/counts")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public SourcingCountsResponse counts(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable UUID projectId,
                                         @Valid @RequestBody SourcingCountsRequest request) {
        return counts.count(principal.requireWorkspaceId(), projectId, request.triageCompanyIds(),
                request.criteria().toCriteria());
    }

    @PostMapping
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ResponseEntity<ExecutiveSourcingRunResponse> start(@AuthenticationPrincipal AuthPrincipal principal,
                                                              @PathVariable UUID projectId,
                                                              @Valid @RequestBody StartExecutiveSourcingRequest request,
                                                              HttpServletRequest httpRequest) {
        ExecutiveSourcingRunResponse run = sourcing.request(principal.userId(), principal.requireWorkspaceId(),
                projectId, request, httpRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(run);
    }

    @GetMapping("/latest")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ResponseEntity<ExecutiveSourcingRunResponse> latest(@AuthenticationPrincipal AuthPrincipal principal,
                                                               @PathVariable UUID projectId) {
        return sourcing.latestOf(principal.requireWorkspaceId(), projectId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{runId}")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ExecutiveSourcingRunResponse status(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable UUID projectId, @PathVariable UUID runId) {
        return sourcing.statusOf(principal.requireWorkspaceId(), projectId, runId);
    }

    @PostMapping("/{runId}/stop")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ExecutiveSourcingRunResponse stop(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable UUID projectId, @PathVariable UUID runId) {
        return sourcing.stop(principal.requireWorkspaceId(), projectId, runId);
    }

    /** {@code companyIds} absent reads every company searched within the people cache's life. */
    @GetMapping("/suggestions")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ExecutiveSuggestionsResponse suggestions(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @PathVariable UUID projectId,
                                                    @RequestParam(required = false) List<UUID> companyIds) {
        return suggestions.read(principal.requireWorkspaceId(), projectId,
                companyIds == null ? List.of() : companyIds);
    }

    @PostMapping("/suggestions/add")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public AddSuggestionsResponse add(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                                      @Valid @RequestBody AddSuggestionsRequest request,
                                      HttpServletRequest httpRequest) {
        return filing.add(principal.userId(), principal.requireWorkspaceId(), projectId, request, httpRequest);
    }
}
