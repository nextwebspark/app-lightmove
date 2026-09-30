package app.lightmove.api.enrichment.peoplesearch.controller;

import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.enrichment.peoplesearch.dto.LocationSuggestionsResponse;
import app.lightmove.api.enrichment.peoplesearch.service.LocationSuggestions;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The People sidebar's Location typeahead: workspace-level, like the company picker, and answering from two letters. */
@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationSuggestionController {

    private final LocationSuggestions locations;

    @GetMapping("/suggest")
    @RequireWorkspacePermission(WorkspaceAction.PROJECT_BROWSE)
    public LocationSuggestionsResponse suggest(@RequestParam(name = "q") String query) {
        return new LocationSuggestionsResponse(locations.suggest(query));
    }
}
