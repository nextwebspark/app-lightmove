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
 * The country vocabulary, served rather than mirrored. Every country input in the SPA reads it, so
 * one catalog answers the company form, the executive drawer, the client registry and the filter —
 * and a spelling added here reaches all of them without a frontend release.
 *
 * <p>Not folded into {@code /companies/facets}: that response is counts the sidebar draws, and a
 * vocabulary is not a count. The markets beside it are the one country fact that <i>is</i> counted,
 * and they are here rather than there so the sidebar's read does not grow a {@code GROUP BY} over the
 * whole universe — which is the reason there has never been a location facet.
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
