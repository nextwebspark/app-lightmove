package app.lightmove.api.strategy.controller;

import app.lightmove.api.core.config.CompanySearchSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.strategy.dto.CompanyResultDto;
import app.lightmove.api.strategy.dto.CompanySuggestionsResponse;
import app.lightmove.api.strategy.dto.FacetsResponse;
import app.lightmove.api.strategy.dto.KeywordSuggestionsResponse;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.strategy.service.CompanySuggestionSearch;
import app.lightmove.api.strategy.service.IndustryAdjacency;
import app.lightmove.api.strategy.service.UniverseFacets;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Workspace-level, read-only reads over the company universe — the sidebar's facets and the pickers'
 * suggestions — gated {@code PROJECT_BROWSE}. A mandate's own filtered list lives under
 * {@code /projects/{projectId}/strategy/companies} behind {@code WORK_VIEW}: it is the search's content.
 */
@RestController
@RequestMapping("/api/v1/companies")
public class CompanySearchController {

    private final ApolloCompanyQueryService companies;
    private final UniverseFacets facets;
    private final IndustryAdjacency adjacency;
    private final CompanySuggestionSearch suggestions;
    private final CompanySearchSettings searchConfig;

    public CompanySearchController(ApolloCompanyQueryService companies, UniverseFacets facets,
                                   IndustryAdjacency adjacency, CompanySuggestionSearch suggestions,
                                   LightMoveProperties properties) {
        this.companies = companies;
        this.facets = facets;
        this.adjacency = adjacency;
        this.suggestions = suggestions;
        this.searchConfig = properties.company().search();
    }

    /** Location is not counted. */
    @GetMapping("/facets")
    @RequireWorkspacePermission(WorkspaceAction.PROJECT_BROWSE)
    public FacetsResponse facets() {
        return new FacetsResponse(
                facets.sectorGroups(),
                adjacency.neighbours(),
                facets.marketSegments(),
                facets.employeeBands(),
                facets.revenueBands());
    }

    /** A blank query returns nothing rather than the head of the universe. */
    @GetMapping("/search")
    @RequireWorkspacePermission(WorkspaceAction.PROJECT_BROWSE)
    public CompanySuggestionsResponse search(@RequestParam(name = "q") String query,
                                             @RequestParam(name = "limit", required = false)
                                             Integer limit) {
        return new CompanySuggestionsResponse(suggestions.suggest(query, limit, 1));
    }

    /** Too short a query answers nothing, as in the company picker. */
    @GetMapping("/keywords")
    @RequireWorkspacePermission(WorkspaceAction.PROJECT_BROWSE)
    public KeywordSuggestionsResponse keywords(@RequestParam(name = "q") String query) {
        String trimmed = suggestions.acceptedQuery(query);
        if (trimmed.length() < searchConfig.keywordMinQueryLength()) {
            return new KeywordSuggestionsResponse(List.of());
        }
        return new KeywordSuggestionsResponse(companies.keywordSuggestions(
                trimmed, searchConfig.keywordSuggestionLimit(), searchConfig.keywordMinCompanies()));
    }

    /** The literal routes above win over this template, so {@code /companies/facets} is not a company. */
    @GetMapping("/{apolloAccountId}")
    @RequireWorkspacePermission(WorkspaceAction.PROJECT_BROWSE)
    public CompanyResultDto byAccountId(@PathVariable String apolloAccountId) {
        return companies.byAccountIds(List.of(apolloAccountId)).stream()
                .findFirst()
                .map(CompanyResultDto::of)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }
}
