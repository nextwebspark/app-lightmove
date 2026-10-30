package app.lightmove.api.pairing.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.pairing.dto.StageLinesCriteria;
import app.lightmove.api.pairing.dto.StageLinesResponse;
import app.lightmove.api.pairing.service.StageLinesService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The Companies grid's lines, a page at a time. WORK_VIEW, as the grid it draws: a client seat reads it too. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/triage/lines")
@RequiredArgsConstructor
public class StageLinesController {

    private final StageLinesService lines;
    private final ProjectAccess projectAccess;

    @GetMapping
    @RequireProjectPermission(ProjectAction.WORK_VIEW)
    public StageLinesResponse page(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable UUID projectId,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) String q,
                                   @RequestParam(required = false) String executiveQuery,
                                   @RequestParam(required = false) List<String> executiveStatuses,
                                   @RequestParam(required = false) String search,
                                   @RequestParam(required = false) String sort,
                                   @RequestParam(required = false) String direction,
                                   @RequestParam(required = false) Integer page,
                                   @RequestParam(required = false) Integer size) {
        UUID workspaceId = principal.requireWorkspaceId();
        return lines.page(workspaceId, projectId,
                new StageLinesCriteria(status, q, executiveQuery, executiveStatuses, search, sort, direction, page,
                        size),
                projectAccess.audienceOf(principal.userId(), workspaceId, projectId));
    }
}
