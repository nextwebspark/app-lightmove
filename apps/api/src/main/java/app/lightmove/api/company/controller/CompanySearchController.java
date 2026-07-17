package app.lightmove.api.company.controller;

import app.lightmove.api.company.dto.CompanyDtos.CompanySearchRequest;
import app.lightmove.api.company.dto.CompanyDtos.CompanySearchResponse;
import app.lightmove.api.company.model.CompanySearchCommand;
import app.lightmove.api.company.service.CompanySearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Semantic search over the shared company universe ({@code app_lm_companies}).
 *
 * <p>Not workspace-scoped: the company universe is reference data shared across every tenant, not a
 * per-workspace resource, so there is nothing here to filter by {@code workspace_id}. No identity check
 * in this method, deliberately — it never used the principal for anything beyond confirming one existed,
 * and that confirmation is {@code SecurityConfig}'s job, not the controller's. That filter chain still
 * requires an authenticated, verified caller in every profile except {@code local}, where it permits this
 * one path unauthenticated for manual testing (see {@code apiChain}'s companies-search rule).
 */
@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanySearchController {

    private final CompanySearchService searchService;

    @PostMapping("/search")
    public ResponseEntity<CompanySearchResponse> search(@Valid @RequestBody CompanySearchRequest request) {
        CompanySearchResponse response = searchService.search(
                new CompanySearchCommand(request.query(), request.page(), request.size()));
        return ResponseEntity.ok(response);
    }
}
