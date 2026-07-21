package app.lightmove.api.company.controller;

import app.lightmove.api.company.constant.CompanySearchOrder;
import app.lightmove.api.company.constant.EmployeeBand;
import app.lightmove.api.company.constant.RevenueBand;
import app.lightmove.api.company.dto.CompanyDtos.EstimateResponse;
import app.lightmove.api.company.dto.CompanyDtos.SearchResponse;
import app.lightmove.api.company.dto.CompanyDtos.SectorsResponse;
import app.lightmove.api.company.dto.CompanyDtos.SuggestionsResponse;
import app.lightmove.api.company.service.CompanyQueryService;
import app.lightmove.api.company.service.CompanyQueryService.ScopeFilter;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reads over the company universe that the Strategy screen searches. It is shared reference data, not
 * workspace-scoped, so a workspace-level PROJECT_BROWSE is the gate: whoever may browse projects may
 * read the sectors, ask for suggestions and size the scope. Nothing here is writable.
 */
@RestController
@RequestMapping("/api/v1/companies")
public class CompanyReferenceController {

    /**
     * The geography catalog's wire values, duplicated from {@code project.constant.GeographyMarket} rather
     * than imported — this controller is {@code company}-feature, and {@code company} stays agnostic of
     * {@code project}'s types. ISO-3166 alpha-2 codes, matching {@code hq_country} verbatim.
     */
    private static final Set<String> MARKET_CODES = Set.of("AE", "SA", "KW", "QA", "BH", "OM");

    private final CompanyQueryService companies;
    private final LightMoveProperties.Company.Search searchConfig;
    private final LightMoveProperties.Company.Estimate estimateConfig;

    public CompanyReferenceController(CompanyQueryService companies, LightMoveProperties properties) {
        this.companies = companies;
        this.searchConfig = properties.company().search();
        this.estimateConfig = properties.company().estimate();
    }

    @GetMapping("/sectors")
    @PreAuthorize("@workspaceAuth.can(principal, 'PROJECT_BROWSE')")
    public ResponseEntity<SectorsResponse> sectors() {
        return ResponseEntity.ok(new SectorsResponse(companies.sectors()));
    }

    @GetMapping("/sectors/suggestions")
    @PreAuthorize("@workspaceAuth.can(principal, 'PROJECT_BROWSE')")
    public ResponseEntity<SuggestionsResponse> suggestions(
            @RequestParam(name = "sector", required = false) List<String> sectors) {
        return ResponseEntity.ok(companies.suggestionsFor(orEmpty(sectors)));
    }

    @GetMapping("/estimate")
    @PreAuthorize("@workspaceAuth.can(principal, 'PROJECT_BROWSE')")
    public ResponseEntity<EstimateResponse> estimate(
            @RequestParam(name = "sector", required = false) List<String> sectors,
            @RequestParam(name = "tag", required = false) List<String> tags,
            @RequestParam(name = "employeeBand", required = false) List<String> employeeBands,
            @RequestParam(name = "revenueBand", required = false) List<String> revenueBands,
            @RequestParam(name = "market", required = false) List<String> markets) {
        List<String> sectorList = orEmpty(sectors);
        List<String> tagList = orEmpty(tags);
        if (sectorList.size() + tagList.size() > estimateConfig.maxLabels()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Too many labels to estimate at once");
        }
        List<String> employeeBandList = resolveBands(employeeBands, EmployeeBand::fromValue, "employee");
        List<String> revenueBandList = resolveBands(revenueBands, RevenueBand::fromValue, "revenue");
        List<String> marketList = resolveMarkets(markets);
        ScopeFilter scope = new ScopeFilter(sectorList, List.of(), tagList, employeeBandList, revenueBandList,
                marketList, List.of(), List.of());
        return ResponseEntity.ok(new EstimateResponse(companies.estimate(scope)));
    }

    /**
     * The company picker: a blank query browses by revenue within the given sectors; any typed text
     * matches names instead, ignoring sector and order — typed text means the user knows the company.
     */
    @GetMapping("/search")
    @PreAuthorize("@workspaceAuth.can(principal, 'PROJECT_BROWSE')")
    public ResponseEntity<SearchResponse> search(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "sector", required = false) List<String> sectors,
            @RequestParam(name = "order", required = false) String order,
            @RequestParam(name = "limit", required = false) Integer limit) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() > searchConfig.maxQueryLength()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "That search is too long");
        }
        int effectiveLimit = limit == null
                ? searchConfig.defaultResultLimit()
                : Math.clamp(limit, 1, searchConfig.maxResultLimit());
        if (!trimmed.isEmpty()) {
            return ResponseEntity.ok(new SearchResponse(companies.search(trimmed, effectiveLimit)));
        }
        return ResponseEntity.ok(new SearchResponse(
                companies.browse(orEmpty(sectors), resolveOrder(order), effectiveLimit)));
    }

    /** Resolve the order token, defaulting to prominent-first; an unknown token is a client bug. */
    private static CompanySearchOrder resolveOrder(String order) {
        if (order == null) {
            return CompanySearchOrder.REVENUE_DESC;
        }
        CompanySearchOrder resolved = CompanySearchOrder.fromValue(order);
        if (resolved == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown search order: " + order);
        }
        return resolved;
    }

    /**
     * A missing repeated param binds to null, and Spring's {@code @DefaultValue("")} would bind an
     * empty query to a single blank element rather than an empty list — the list-binding trap this
     * codebase has been bitten by. Normalising to an empty list here keeps both cases honest.
     */
    private static List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    /** Validate each wire band value against its fixed catalog, rejecting anything unrecognized. */
    private static <T> List<String> resolveBands(List<String> values, Function<String, T> fromValue, String axis) {
        List<String> bandValues = orEmpty(values);
        for (String value : bandValues) {
            if (fromValue.apply(value) == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown " + axis + " band: " + value);
            }
        }
        return bandValues;
    }

    /** Validate each wire market code against the fixed catalog, rejecting anything unrecognized. */
    private static List<String> resolveMarkets(List<String> values) {
        List<String> marketValues = orEmpty(values);
        for (String value : marketValues) {
            if (!MARKET_CODES.contains(value)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown market: " + value);
            }
        }
        return marketValues;
    }
}
