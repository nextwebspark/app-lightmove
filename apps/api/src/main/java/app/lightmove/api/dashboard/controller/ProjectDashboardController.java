package app.lightmove.api.dashboard.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.dashboard.constant.DashboardScope;
import app.lightmove.api.dashboard.dto.ChartQueryResponse;
import app.lightmove.api.dashboard.dto.ChartResultResponse;
import app.lightmove.api.dashboard.dto.DashboardResponse;
import app.lightmove.api.dashboard.dto.RunChartQueryRequest;
import app.lightmove.api.dashboard.dto.SaveDashboardRequest;
import app.lightmove.api.dashboard.model.ChartQueryContext;
import app.lightmove.api.dashboard.service.ChartQueryService;
import app.lightmove.api.dashboard.service.DashboardService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A position's custom dashboards and the library they draw from. {@code WORK_EXECUTE} throughout: a client seat
 * builds and reads none, and keeps the report's Overview.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/dashboards")
@RequiredArgsConstructor
public class ProjectDashboardController {

    private final DashboardService dashboards;
    private final ChartQueryService queries;

    @GetMapping
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public List<DashboardResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable UUID projectId) {
        return dashboards.list(principal.userId(), principal.requireWorkspaceId(), projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public DashboardResponse create(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                                    @Valid @RequestBody SaveDashboardRequest request) {
        return dashboards.create(principal.userId(), principal.requireWorkspaceId(), projectId, request);
    }

    @PutMapping("/{dashboardId}")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public DashboardResponse update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                                    @PathVariable UUID dashboardId, @Valid @RequestBody SaveDashboardRequest request) {
        return dashboards.update(principal.userId(), principal.requireWorkspaceId(), projectId, dashboardId, request);
    }

    @DeleteMapping("/{dashboardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                       @PathVariable UUID dashboardId) {
        dashboards.delete(principal.userId(), principal.requireWorkspaceId(), projectId, dashboardId);
    }

    @GetMapping("/library")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public List<ChartQueryResponse> library(@PathVariable UUID projectId) {
        return queries.library(DashboardScope.PROJECT);
    }

    @PostMapping("/query/{queryId}")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ChartResultResponse run(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                                   @PathVariable String queryId, @Valid @RequestBody RunChartQueryRequest request) {
        ChartQueryContext context = new ChartQueryContext(principal.userId(), principal.requireWorkspaceId(),
                projectId);
        return queries.run(context, DashboardScope.PROJECT, queryId, request.options());
    }
}
