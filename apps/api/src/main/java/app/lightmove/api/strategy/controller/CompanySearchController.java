package app.lightmove.api.strategy.controller;

import app.lightmove.api.core.config.CompanySearchSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.text.service.LinkedInCompanySlug;
import app.lightmove.api.core.text.service.WebsiteDomain;
import app.lightmove.api.strategy.dto.CompanyMatchResponse;
import app.lightmove.api.strategy.dto.CompanySuggestion;
import app.lightmove.api.strategy.dto.CompanySuggestionsResponse;
import app.lightmove.api.strategy.dto.FacetsResponse;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The workspace-level reads over the company universe: what the filter sidebar can offer, and what a
 * company picker suggests. Shared reference data rather than workspace-scoped content, so
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
    private final CompanySearchSettings searchConfig;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public CompanySearchController(ApolloCompanyQueryService companies, LightMoveProperties properties) {
        this.companies = companies;
        this.searchConfig = properties.company().search();
    }

    /** Everything the five filter accordions render, counted over the whole universe. */
    @GetMapping("/facets")
    @PreAuthorize("@workspaceAuthorizer.can(principal, 'PROJECT_BROWSE')")
    public ResponseEntity<FacetsResponse> facets() {
        return ResponseEntity.ok(new FacetsResponse(
                companies.sectorGroups(),
                companies.marketSegmentFacets(),
                companies.countryFacets(),
                companies.employeeBandFacets(),
                companies.revenueBandFacets()));
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
        String trimmed = query.trim();
        if (trimmed.length() > searchConfig.maxQueryLength()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "q exceeds " + searchConfig.maxQueryLength() + " characters");
        }
        if (trimmed.isEmpty()) {
            return ResponseEntity.ok(new CompanySuggestionsResponse(List.of()));
        }
        // Refused rather than clamped, matching every other list read: a silently narrowed limit is a
        // wrong answer to a stated request, and the caller cannot tell it got one.
        if (limit != null && (limit < 1 || limit > searchConfig.maxResultLimit())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "limit must be between 1 and " + searchConfig.maxResultLimit());
        }
        int resolvedLimit = limit == null ? searchConfig.defaultResultLimit() : limit;
        return ResponseEntity.ok(new CompanySuggestionsResponse(
                companies.typeahead(trimmed, resolvedLimit).stream()
                        .map(CompanySearchController::toSuggestion)
                        .toList()));
    }

    /**
     * Does the universe publish the company on this page? The lookup behind the Chrome extension's
     * capture: it decides whether a captured company is filed under its Apollo identity, with the
     * snapshot resolved server-side, or as a company of its own.
     *
     * <p>Both parameters are raw as the page gave them and are normalised here — a domain to its
     * registrable host, a LinkedIn URL to its company slug — so a caller never has to know the shape
     * the universe stores. Naming neither is a miss rather than an error: a page with no domain and no
     * LinkedIn link is a page the universe cannot be asked about.
     */
    @GetMapping("/resolve")
    @PreAuthorize("@workspaceAuthorizer.can(principal, 'PROJECT_BROWSE')")
    public ResponseEntity<CompanyMatchResponse> resolve(
            @RequestParam(name = "domain", required = false) String domain,
            @RequestParam(name = "linkedinUrl", required = false) String linkedinUrl) {
        return ResponseEntity.ok(companies
                .byDomainOrLinkedIn(WebsiteDomain.of(domain), LinkedInCompanySlug.of(linkedinUrl))
                .map(row -> new CompanyMatchResponse(true, toSuggestion(row)))
                .orElseGet(CompanyMatchResponse::noMatch));
    }

    private static CompanySuggestion toSuggestion(CompanyRow row) {
        return new CompanySuggestion(row.apolloAccountId(), row.companyName(), row.industry(),
                row.companyCity(), row.companyCountry(), row.website(), row.logoUrl(),
                row.numEmployees());
    }
}
