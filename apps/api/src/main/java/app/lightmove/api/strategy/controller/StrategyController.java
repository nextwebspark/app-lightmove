package app.lightmove.api.strategy.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.strategy.dto.PutOffLimitsRequest;
import app.lightmove.api.strategy.dto.PutStrategyFilterRequest;
import app.lightmove.api.strategy.dto.RenameSearchRequest;
import app.lightmove.api.strategy.dto.SaveSearchRequest;
import app.lightmove.api.strategy.dto.SavedSearchResponse;
import app.lightmove.api.strategy.dto.StrategyCompaniesResponse;
import app.lightmove.api.strategy.dto.StrategyResponse;
import app.lightmove.api.strategy.service.StrategySearchService;
import app.lightmove.api.strategy.service.StrategyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * The search of one mandate: its filter, its off-limits list, the companies they select, and the
 * searches saved against them.
 *
 * <p>Reading needs a seat on the project (WORK_VIEW, which every seated role holds including CLIENT),
 * with the workspace-admin bypass so an admin sees every project. A mandate's scope is team content,
 * not browsable to the whole workspace — which is the line between this controller and
 * {@code CompanySearchController}, where the market's own shape is a workspace-level read. Writing
 * is PROJECT_EDIT on the seat. The workspace comes from the principal, never the path.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/strategy")
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyService strategy;
    private final StrategySearchService searches;

    @GetMapping
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_VIEW')")
    public ResponseEntity<StrategyResponse> get(@AuthenticationPrincipal AuthPrincipal principal,
                                                @PathVariable UUID projectId) {
        return ResponseEntity.ok(strategy.get(principal.requireWorkspaceId(), projectId));
    }

    @PutMapping("/filter")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<StrategyResponse> putFilter(@AuthenticationPrincipal AuthPrincipal principal,
                                                      @PathVariable UUID projectId,
                                                      @Valid @RequestBody PutStrategyFilterRequest request,
                                                      HttpServletRequest httpRequest) {
        return ResponseEntity.ok(strategy.putFilter(principal.userId(), principal.requireWorkspaceId(),
                projectId, request, httpRequest));
    }

    @PutMapping("/off-limits")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<StrategyResponse> putOffLimits(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @PathVariable UUID projectId,
                                                         @Valid @RequestBody PutOffLimitsRequest request,
                                                         HttpServletRequest httpRequest) {
        return ResponseEntity.ok(strategy.putOffLimits(principal.userId(), principal.requireWorkspaceId(),
                projectId, request, httpRequest));
    }

    /**
     * The results table. The scope is resolved server-side from the saved filter; the caller supplies
     * only the name query, the page and the sort, none of which widens what they can see.
     */
    @GetMapping("/companies")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_VIEW')")
    public ResponseEntity<StrategyCompaniesResponse> companies(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(strategy.companies(principal.requireWorkspaceId(), projectId, query,
                sort, direction, page, size));
    }

    /**
     * Save the mandate's current filter under a name. Saving is an edit to the mandate's own working
     * set, so it takes PROJECT_EDIT — a CLIENT seat may read a search but not leave one behind.
     */
    @PostMapping("/searches")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<SavedSearchResponse> saveSearch(@AuthenticationPrincipal AuthPrincipal principal,
                                                          @PathVariable UUID projectId,
                                                          @Valid @RequestBody SaveSearchRequest request,
                                                          HttpServletRequest httpRequest) {
        SavedSearchResponse saved = searches.save(principal.userId(), principal.requireWorkspaceId(),
                projectId, request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/searches/{searchId}")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<SavedSearchResponse> renameSearch(@AuthenticationPrincipal AuthPrincipal principal,
                                                            @PathVariable UUID projectId,
                                                            @PathVariable UUID searchId,
                                                            @Valid @RequestBody RenameSearchRequest request,
                                                            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(searches.rename(principal.userId(), principal.requireWorkspaceId(),
                projectId, searchId, request, httpRequest));
    }

    @DeleteMapping("/searches/{searchId}")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")
    public ResponseEntity<Void> deleteSearch(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable UUID projectId,
                                             @PathVariable UUID searchId,
                                             HttpServletRequest httpRequest) {
        searches.delete(principal.userId(), principal.requireWorkspaceId(), projectId, searchId,
                httpRequest);
        return ResponseEntity.noContent().build();
    }
}
