package app.lightmove.api.strategy.controller;

import app.lightmove.api.core.config.CompanySearchSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.strategy.dto.CompanySuggestion;
import app.lightmove.api.strategy.dto.CompanySuggestionsResponse;
import app.lightmove.api.strategy.dto.FacetsResponse;
import app.lightmove.api.strategy.dto.KeywordSuggestionsResponse;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.strategy.service.CompanyFacetService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The workspace-level reads over the company universe: what the filter sidebar can offer, and what a
 * company or keyword picker suggests. Shared reference data rather than workspace-scoped content, so
 * {@code PROJECT_BROWSE} is the gate — whoever may browse projects may see the shape of the market.
 * Nothing here is writable, and nothing here is scoped to a mandate.
 *
 * <p>A mandate's own filtered list is deliberately <i>not</i> here, and neither are the counts its
 * filter cuts. Both live under {@code /projects/{projectId}/strategy} behind the project-level
 * {@code WORK_VIEW}, because which companies a search is looking at — and how many each remaining
 * chip would still reach — is the search's content, not the market's shape.
 */
@RestController
@RequestMapping("/api/v1/companies")
public class CompanySearchController {

    private final ApolloCompanyQueryService companies;
    private final CompanyFacetService facets;
    private final CompanySearchSettings searchConfig;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public CompanySearchController(ApolloCompanyQueryService companies, CompanyFacetService facets,
                                   LightMoveProperties properties) {
        this.companies = companies;
        this.facets = facets;
        this.searchConfig = properties.company().search();
    }

    /**
     * What the five filter accordions can offer: the vocabulary, the order it renders in, and how big
     * each option is across the whole universe.
     *
     * <p>The same for every mandate and stable until the pipeline next loads, which is what makes it
     * cacheable. The numbers a sidebar actually shows are the ones its own selection cuts — those
     * come from the mandate's {@code /strategy/facet-counts}, and the order still comes from here so
     * a row cannot re-rank itself under the hand that just clicked the row above it.
     */
    @GetMapping("/facets")
    @PreAuthorize("@workspaceAuthorizer.can(principal, 'PROJECT_BROWSE')")
    public ResponseEntity<FacetsResponse> facets() {
        return ResponseEntity.ok(facets.universeFacets());
    }

    /**
     * Name search for the company pickers. A blank query returns nothing rather than the head of the
     * universe: a picker that offers six arbitrary companies before a key is pressed suggests they
     * were chosen for a reason.
     */
    @GetMapping("/search")
    @PreAuthorize("@workspaceAuthorizer.can(principal, 'PROJECT_BROWSE')")
    public ResponseEntity<CompanySuggestionsResponse> search(@RequestParam(name = "q") String query,
                                                             @RequestParam(name = "limit", required = false)
                                                             Integer limit) {
        String trimmed = accepted(query);
        if (trimmed.isEmpty()) {
            return ResponseEntity.ok(new CompanySuggestionsResponse(List.of()));
        }
        return ResponseEntity.ok(new CompanySuggestionsResponse(
                companies.typeahead(trimmed, resolvedLimit(limit, searchConfig.defaultResultLimit())).stream()
                        .map(CompanySearchController::toSuggestion)
                        .toList()));
    }

    /**
     * The Company Keywords box, on the same rule as the company picker: too short a query answers
     * nothing rather than the head of the universe.
     */
    @GetMapping("/keywords")
    @PreAuthorize("@workspaceAuthorizer.can(principal, 'PROJECT_BROWSE')")
    public ResponseEntity<KeywordSuggestionsResponse> keywords(@RequestParam(name = "q") String query) {
        String trimmed = accepted(query);
        if (trimmed.length() < searchConfig.keywordMinQueryLength()) {
            return ResponseEntity.ok(new KeywordSuggestionsResponse(List.of()));
        }
        return ResponseEntity.ok(new KeywordSuggestionsResponse(facets.keywordSuggestions(
                trimmed, searchConfig.keywordSuggestionLimit(), searchConfig.keywordMinCompanies())));
    }

    private String accepted(String query) {
        String trimmed = query.trim();
        if (trimmed.length() > searchConfig.maxQueryLength()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "q exceeds " + searchConfig.maxQueryLength() + " characters");
        }
        return trimmed;
    }

    /**
     * Refused rather than clamped, matching every other list read: a silently narrowed limit is a
     * wrong answer to a stated request, and the caller cannot tell it got one.
     */
    private int resolvedLimit(Integer limit, int fallback) {
        if (limit == null) {
            return fallback;
        }
        if (limit < 1 || limit > searchConfig.maxResultLimit()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "limit must be between 1 and " + searchConfig.maxResultLimit());
        }
        return limit;
    }

    private static CompanySuggestion toSuggestion(CompanyRow row) {
        return new CompanySuggestion(row.apolloAccountId(), row.companyName(), row.industry(),
                row.companyCity(), row.companyCountry(), row.website(), row.logoUrl(),
                row.numEmployees());
    }
}
