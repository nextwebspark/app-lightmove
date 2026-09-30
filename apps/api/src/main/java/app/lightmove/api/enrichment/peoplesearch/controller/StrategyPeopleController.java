package app.lightmove.api.enrichment.peoplesearch.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.enrichment.peoplesearch.dto.AddPeopleRequest;
import app.lightmove.api.enrichment.peoplesearch.dto.AddPeopleResponse;
import app.lightmove.api.enrichment.peoplesearch.dto.PeopleCountResponse;
import app.lightmove.api.enrichment.peoplesearch.dto.PeopleSearchPageResponse;
import app.lightmove.api.enrichment.peoplesearch.service.StrategyPeopleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Strategy's People mode over the mandate's stored people filter. {@code WORK_EXECUTE}, never
 * {@code WORK_VIEW}: every page may spend search credits, so a client seat reaches none of it. The
 * search is a POST for the same reason — it is not a safe read.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/strategy/people")
@RequiredArgsConstructor
public class StrategyPeopleController {

    private final StrategyPeopleService people;

    @GetMapping("/count")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public PeopleCountResponse count(@AuthenticationPrincipal AuthPrincipal principal,
                                     @PathVariable UUID projectId) {
        return people.count(principal.requireWorkspaceId(), projectId);
    }

    @PostMapping("/search")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public PeopleSearchPageResponse search(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable UUID projectId,
                                           @RequestParam(defaultValue = "1") int page,
                                           HttpServletRequest httpRequest) {
        return people.search(principal.userId(), principal.requireWorkspaceId(), projectId, page, httpRequest);
    }

    @PostMapping("/add")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public AddPeopleResponse add(@AuthenticationPrincipal AuthPrincipal principal,
                                 @PathVariable UUID projectId,
                                 @Valid @RequestBody AddPeopleRequest request) {
        return people.add(principal.userId(), principal.requireWorkspaceId(), projectId, request);
    }
}
