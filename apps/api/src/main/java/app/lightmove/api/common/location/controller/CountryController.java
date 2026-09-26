package app.lightmove.api.common.location.controller;

import app.lightmove.api.common.location.dto.CountriesResponse;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.strategy.model.ScopeBreakdown;
import app.lightmove.api.strategy.service.UniverseFacets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The country vocabulary, served so every SPA picker reads one catalog. Not part of
 * {@code /companies/facets}: its markets are here so the sidebar read grows no {@code GROUP BY}.
 */
@RestController
@RequestMapping("/api/v1/countries")
@RequiredArgsConstructor
public class CountryController {

    /** As many Location chips as the sidebar has ever shown; beyond it the tail is a long thin one. */
    private static final int MARKET_LIMIT = 8;

    private final UniverseFacets facets;

    @GetMapping
    @RequireWorkspacePermission(WorkspaceAction.PROJECT_BROWSE)
    public CountriesResponse countries() {
        List<String> markets = facets.countries(MARKET_LIMIT).stream()
                .map(ScopeBreakdown::label)
                .toList();
        return new CountriesResponse(Countries.all(), markets);
    }
}
