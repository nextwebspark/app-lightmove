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
 * The workspace-level reads over the company universe: what the filter sidebar can offer, and what a
 * company or keyword picker suggests. Shared reference data rather than workspace-scoped content, so
 * {@code PROJECT_BROWSE} is the gate — whoever may browse projects may see the shape of the market.
 * Nothing here is writable, and nothing here is scoped to a mandate.
 *
 * <p>A mandate's own filtered list is deliberately <i>not</i> here. It lives under
 * {@code /projects/{projectId}/strategy/companies} behind the project-level {@code WORK_VIEW},
 * because which companies a search is looking at is the search's content, not the market's shape.
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

    /** Everything the filter accordions count over the whole universe — Location is not counted. */
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

    /**
     * Name search for the company pickers. A blank query returns nothing rather than the head of the
     * universe, which would suggest the six rows were chosen for a reason.
     */
    @GetMapping("/search")
    @RequireWorkspacePermission(WorkspaceAction.PROJECT_BROWSE)
    public CompanySuggestionsResponse search(@RequestParam(name = "q") String query,
                                             @RequestParam(name = "limit", required = false)
                                             Integer limit) {
        return new CompanySuggestionsResponse(suggestions.suggest(query, limit, 1));
    }

    /**
     * The Company Keywords box, on the same rule as the company picker: too short a query answers
     * nothing rather than the head of the universe.
     */
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

    /**
     * One company of the universe — the record behind a picked suggestion. Read-only, like everything
     * here: a company is <b>taken</b> into a mandate through {@code POST /projects/{projectId}/triage},
     * which resolves this same row server-side rather than trusting what the client saw.
     *
     * <p>The literal routes above still win over this path variable — Spring matches an exact segment
     * before a template — so {@code /companies/facets} is the facets read, not a company called
     * "facets".
     */
    @GetMapping("/{apolloAccountId}")
    @RequireWorkspacePermission(WorkspaceAction.PROJECT_BROWSE)
    public CompanyResultDto byAccountId(@PathVariable String apolloAccountId) {
        return companies.byAccountIds(List.of(apolloAccountId)).stream()
                .findFirst()
                .map(CompanyResultDto::of)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }
}
