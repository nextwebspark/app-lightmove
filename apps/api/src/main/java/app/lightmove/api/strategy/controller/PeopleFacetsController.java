package app.lightmove.api.strategy.controller;

import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.strategy.dto.PeopleFacetsResponse;
import app.lightmove.api.strategy.service.PeopleSearchVocabulary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The People sidebar's vocabulary, served beside the company universe's reads; the literal route wins over the
 * universe's {@code /companies/{apolloAccountId}} template.
 */
@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class PeopleFacetsController {

    private final PeopleSearchVocabulary peopleVocabulary;

    /** ContactOut's accepted values for the People sidebar; not counted — the count is the vendor's. */
    @GetMapping("/people-facets")
    @RequireWorkspacePermission(WorkspaceAction.PROJECT_BROWSE)
    public PeopleFacetsResponse peopleFacets() {
        return peopleVocabulary.facets();
    }
}
