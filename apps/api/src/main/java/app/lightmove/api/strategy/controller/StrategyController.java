package app.lightmove.api.strategy.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.strategy.dto.PutOffLimitsRequest;
import app.lightmove.api.strategy.dto.PutStrategyFilterRequest;
import app.lightmove.api.strategy.dto.SaveSearchRequest;
import app.lightmove.api.strategy.dto.SavedSearchResponse;
import app.lightmove.api.strategy.dto.StrategyCompaniesResponse;
import app.lightmove.api.strategy.dto.StrategyResponse;
import app.lightmove.api.strategy.dto.UpdateSearchRequest;
import app.lightmove.api.strategy.service.StrategySearchService;
import app.lightmove.api.strategy.service.StrategyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * One mandate's filter, off-limits list, results and saved searches. Reading is WORK_VIEW on a seat
 * (clients included); writing is PROJECT_EDIT. A private search that is not the caller's answers 404.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/strategy")
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyService strategy;
    private final StrategySearchService searches;

    @GetMapping
    @RequireProjectPermission(ProjectAction.WORK_VIEW)
    public StrategyResponse get(@AuthenticationPrincipal AuthPrincipal principal,
                                @PathVariable UUID projectId) {
        return strategy.get(principal.userId(), principal.requireWorkspaceId(),
                projectId);
    }

    @PutMapping("/filter")
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    public StrategyResponse putFilter(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable UUID projectId,
                                      @Valid @RequestBody PutStrategyFilterRequest request,
                                      HttpServletRequest httpRequest) {
        return strategy.putFilter(principal.userId(), principal.requireWorkspaceId(),
                projectId, request, httpRequest);
    }

    @PutMapping("/off-limits")
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    public StrategyResponse putOffLimits(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable UUID projectId,
                                         @Valid @RequestBody PutOffLimitsRequest request,
                                         HttpServletRequest httpRequest) {
        return strategy.putOffLimits(principal.userId(), principal.requireWorkspaceId(),
                projectId, request, httpRequest);
    }

    @GetMapping("/companies")
    @RequireProjectPermission(ProjectAction.WORK_VIEW)
    public StrategyCompaniesResponse companies(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return strategy.companies(principal.requireWorkspaceId(), projectId, query,
                sort, direction, page, size);
    }

    @PostMapping("/searches")
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    @ResponseStatus(HttpStatus.CREATED)
    public SavedSearchResponse saveSearch(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable UUID projectId,
                                          @Valid @RequestBody SaveSearchRequest request,
                                          HttpServletRequest httpRequest) {
        SavedSearchResponse saved = searches.save(principal.userId(), principal.requireWorkspaceId(),
                projectId, request, httpRequest);
        return saved;
    }

    @PatchMapping("/searches/{searchId}")
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    public SavedSearchResponse updateSearch(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable UUID projectId,
                                            @PathVariable UUID searchId,
                                            @Valid @RequestBody UpdateSearchRequest request,
                                            HttpServletRequest httpRequest) {
        return searches.update(principal.userId(), principal.requireWorkspaceId(),
                projectId, searchId, request, httpRequest);
    }

    /** No body: the server reads the stored filter. */
    @PutMapping("/searches/{searchId}/filter")
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    public SavedSearchResponse putSearchFilter(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable UUID projectId,
                                               @PathVariable UUID searchId,
                                               HttpServletRequest httpRequest) {
        return searches.updateFilter(principal.userId(),
                principal.requireWorkspaceId(), projectId, searchId, httpRequest);
    }

    @DeleteMapping("/searches/{searchId}")
    @RequireProjectPermission(ProjectAction.PROJECT_EDIT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSearch(@AuthenticationPrincipal AuthPrincipal principal,
                             @PathVariable UUID projectId,
                             @PathVariable UUID searchId,
                             HttpServletRequest httpRequest) {
        searches.delete(principal.userId(), principal.requireWorkspaceId(), projectId, searchId,
                httpRequest);
    }
}
