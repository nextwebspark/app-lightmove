package app.lightmove.api.company.controller;

import app.lightmove.api.company.dto.CompanyDtos.EstimateResponse;
import app.lightmove.api.company.dto.CompanyDtos.SectorsResponse;
import app.lightmove.api.company.dto.CompanyDtos.SuggestionsResponse;
import app.lightmove.api.company.service.CompanyQueryService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.List;
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
            @RequestParam(name = "tag", required = false) List<String> tags) {
        List<String> sectorList = orEmpty(sectors);
        List<String> tagList = orEmpty(tags);
        if (sectorList.size() + tagList.size() > MAX_ESTIMATE_LABELS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Too many labels to estimate at once");
        }
        return ResponseEntity.ok(new EstimateResponse(companies.estimate(sectorList, tagList)));
    }

    /**
     * A missing repeated param binds to null, and Spring's {@code @DefaultValue("")} would bind an
     * empty query to a single blank element rather than an empty list — the list-binding trap this
     * codebase has been bitten by. Normalising to an empty list here keeps both cases honest.
     */
    private static List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }
}
