package app.lightmove.api.company.controller;

import app.lightmove.api.company.dto.CompanyDtos.EstimateResponse;
import app.lightmove.api.company.dto.CompanyDtos.SectorsResponse;
import app.lightmove.api.company.dto.CompanyDtos.SuggestionsResponse;
import app.lightmove.api.company.service.CompanyQueryService;
import app.lightmove.api.company.service.CompanyQueryService.Range;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class CompanyReferenceController {

    /** A generous ceiling on how many labels one estimate may carry — a scope, not an attack. */
    private static final int MAX_ESTIMATE_LABELS = 100;

    /**
     * The headcount bands' wire value → bounds, duplicated from {@code project.constant.EmployeeBand}
     * rather than imported: this controller is {@code company}-feature, and {@code company} stays
     * agnostic of {@code project}'s types (see {@link CompanyQueryService}'s class doc). Inclusive
     * {@code [min, max]}, a {@code null} max is the open-ended top band.
     */
    private static final Map<String, Range> EMPLOYEE_BANDS = Map.ofEntries(
            Map.entry("1-10", new Range(1L, 10L)),
            Map.entry("11-50", new Range(11L, 50L)),
            Map.entry("51-200", new Range(51L, 200L)),
            Map.entry("201-500", new Range(201L, 500L)),
            Map.entry("501-1000", new Range(501L, 1000L)),
            Map.entry("1001-5000", new Range(1001L, 5000L)),
            Map.entry("5001-10000", new Range(5001L, 10000L)),
            Map.entry("10000+", new Range(10001L, null)));

    /**
     * The revenue bands' wire value → bounds (USD), duplicated for the same reason as
     * {@link #EMPLOYEE_BANDS}. Half-open {@code [min, max)}; {@code null} min is the open-ended bottom
     * band, {@code null} max the open-ended top band.
     */
    private static final Map<String, Range> REVENUE_BANDS = Map.ofEntries(
            Map.entry("<5M", new Range(null, 5_000_000L)),
            Map.entry("5M-25M", new Range(5_000_000L, 25_000_000L)),
            Map.entry("25M-100M", new Range(25_000_000L, 100_000_000L)),
            Map.entry("100M-500M", new Range(100_000_000L, 500_000_000L)),
            Map.entry("500M-1B", new Range(500_000_000L, 1_000_000_000L)),
            Map.entry("1B-5B", new Range(1_000_000_000L, 5_000_000_000L)),
            Map.entry("5B+", new Range(5_000_000_000L, null)));

    private final CompanyQueryService companies;

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
            @RequestParam(name = "revenueBand", required = false) List<String> revenueBands) {
        List<String> sectorList = orEmpty(sectors);
        List<String> tagList = orEmpty(tags);
        if (sectorList.size() + tagList.size() > MAX_ESTIMATE_LABELS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Too many labels to estimate at once");
        }
        List<Range> employeeRanges = resolveBands(employeeBands, EMPLOYEE_BANDS, "employee");
        List<Range> revenueRanges = resolveBands(revenueBands, REVENUE_BANDS, "revenue");
        return ResponseEntity.ok(new EstimateResponse(
                companies.estimate(sectorList, tagList, employeeRanges, revenueRanges)));
    }

    /**
     * A missing repeated param binds to null, and Spring's {@code @DefaultValue("")} would bind an
     * empty query to a single blank element rather than an empty list — the list-binding trap this
     * codebase has been bitten by. Normalising to an empty list here keeps both cases honest.
     */
    private static List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    /** Resolve each wire band value to its bounds, rejecting anything not in the fixed catalog. */
    private static List<Range> resolveBands(List<String> values, Map<String, Range> catalog, String axis) {
        List<String> bandValues = orEmpty(values);
        List<Range> ranges = new ArrayList<>();
        for (String value : bandValues) {
            Range range = catalog.get(value);
            if (range == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown " + axis + " band: " + value);
            }
            ranges.add(range);
        }
        return ranges;
    }
}
