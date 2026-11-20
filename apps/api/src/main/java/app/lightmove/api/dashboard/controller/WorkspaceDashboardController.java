package app.lightmove.api.dashboard.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
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
import org.springframework.security.access.prepost.PreAuthorize;
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
 * The workspace's custom dashboards, across every position the member may browse. Staff only, and a 404 for a pure
 * client, who is not told the surface exists.
 */
@RestController
@RequestMapping("/api/v1/dashboards")
@RequiredArgsConstructor
@PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
public class WorkspaceDashboardController {

    private final DashboardService dashboards;
    private final ChartQueryService queries;

    @GetMapping
    public List<DashboardResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return dashboards.list(principal.userId(), principal.requireWorkspaceId(), null);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DashboardResponse create(@AuthenticationPrincipal AuthPrincipal principal,
                                    @Valid @RequestBody SaveDashboardRequest request) {
        return dashboards.create(principal.userId(), principal.requireWorkspaceId(), null, request);
    }

    @PutMapping("/{dashboardId}")
    public DashboardResponse update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID dashboardId,
                                    @Valid @RequestBody SaveDashboardRequest request) {
        return dashboards.update(principal.userId(), principal.requireWorkspaceId(), null, dashboardId, request);
    }

    @DeleteMapping("/{dashboardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID dashboardId) {
        dashboards.delete(principal.userId(), principal.requireWorkspaceId(), null, dashboardId);
    }

    @GetMapping("/library")
    public List<ChartQueryResponse> library() {
        return queries.library(DashboardScope.WORKSPACE);
    }

    @PostMapping("/query/{queryId}")
    public ChartResultResponse run(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String queryId,
                                   @Valid @RequestBody RunChartQueryRequest request) {
        ChartQueryContext context = new ChartQueryContext(principal.userId(), principal.requireWorkspaceId(), null);
        return queries.run(context, DashboardScope.WORKSPACE, queryId, request.options());
    }
}
