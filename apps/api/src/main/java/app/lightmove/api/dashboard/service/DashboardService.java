package app.lightmove.api.dashboard.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.dashboard.constant.ChartType;
import app.lightmove.api.dashboard.constant.DashboardScope;
import app.lightmove.api.dashboard.constant.WidgetSize;
import app.lightmove.api.dashboard.dto.DashboardResponse;
import app.lightmove.api.dashboard.dto.DashboardWidgetDto;
import app.lightmove.api.dashboard.dto.SaveDashboardRequest;
import app.lightmove.api.dashboard.model.ChartQuery;
import app.lightmove.api.dashboard.model.Dashboard;
import app.lightmove.api.dashboard.model.DashboardWidget;
import app.lightmove.api.dashboard.repository.DashboardRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Members' dashboards on a position ({@code projectId}) or across the workspace (null). A dashboard is its owner's;
 * shared, the rest of the staff who can reach its scope open it read-only. Every chart on it is checked against the
 * library on save; one whose query has since been retired is kept as it was, so it can still be removed.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardRepository dashboards;
    private final ChartQueryService queries;

    @Transactional(readOnly = true)
    public List<DashboardResponse> list(UUID userId, UUID workspaceId, UUID projectId) {
        List<Dashboard> found = projectId == null
                ? dashboards.findVisibleOnWorkspace(workspaceId, userId)
                : dashboards.findVisibleOnProject(workspaceId, projectId, userId);
        return found.stream().map(dashboard -> toResponse(dashboard, userId)).toList();
    }

    @Transactional
    public DashboardResponse create(UUID userId, UUID workspaceId, UUID projectId, SaveDashboardRequest request) {
        Dashboard dashboard = Dashboard.startFor(workspaceId, projectId, userId);
        dashboard.record(request.name().strip(), request.shared(), widgetsOf(scopeOf(projectId), request, List.of()));
        return toResponse(dashboards.save(dashboard), userId);
    }

    @Transactional
    public DashboardResponse update(UUID userId, UUID workspaceId, UUID projectId, UUID dashboardId,
                                    SaveDashboardRequest request) {
        Dashboard dashboard = requireOwned(userId, workspaceId, projectId, dashboardId);
        if (request.version() == null || request.version() != dashboard.getVersion()) {
            throw ApiException.of(ErrorCode.DASHBOARD_STALE);
        }
        dashboard.record(request.name().strip(), request.shared(),
                widgetsOf(scopeOf(projectId), request, dashboard.getWidgets()));
        return toResponse(dashboards.saveAndFlush(dashboard), userId);
    }

    @Transactional
    public void delete(UUID userId, UUID workspaceId, UUID projectId, UUID dashboardId) {
        dashboards.delete(requireOwned(userId, workspaceId, projectId, dashboardId));
    }

    private Dashboard requireOwned(UUID userId, UUID workspaceId, UUID projectId, UUID dashboardId) {
        Dashboard dashboard = dashboards.findByIdAndWorkspaceId(dashboardId, workspaceId)
                .filter(found -> Objects.equals(found.getProjectId(), projectId))
                .filter(found -> found.isOwnedBy(userId) || found.isShared())
                .orElseThrow(() -> ApiException.of(ErrorCode.DASHBOARD_NOT_FOUND));
        if (!dashboard.isOwnedBy(userId)) {
            throw ApiException.of(ErrorCode.DASHBOARD_READ_ONLY);
        }
        return dashboard;
    }

    private List<DashboardWidget> widgetsOf(DashboardScope scope, SaveDashboardRequest request,
                                            List<DashboardWidget> before) {
        if (request.widgets().size() > Dashboard.MAX_WIDGETS) {
            throw ApiException.of(ErrorCode.DASHBOARD_TOO_MANY_WIDGETS);
        }
        Set<String> keptRetired = before.stream().map(DashboardWidget::queryId).collect(Collectors.toSet());
        return request.widgets().stream().map(widget -> checked(scope, widget, keptRetired)).toList();
    }

    private DashboardWidget checked(DashboardScope scope, DashboardWidgetDto widget, Set<String> alreadyStored) {
        if (WidgetSize.fromValue(widget.size()) == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown widget size " + widget.size());
        }
        String title = widget.title() == null || widget.title().isBlank() ? null : widget.title().strip();
        ChartQuery query;
        try {
            query = queries.require(scope, widget.queryId());
        } catch (ApiException retired) {
            if (!alreadyStored.contains(widget.queryId())) {
                throw retired;
            }
            return new DashboardWidget(widget.queryId(), widget.chart(), widget.options(), title, widget.size());
        }
        ChartType chart = ChartType.fromValue(widget.chart());
        if (chart == null || !ChartRules.allowedBeforeData(query.shape(), query.noSum()).contains(chart)) {
            throw new ApiException(ErrorCode.CHART_TYPE_NOT_ALLOWED,
                    "Query " + query.id() + " cannot be drawn as " + widget.chart());
        }
        ChartQueryService.requireValidOptions(query, widget.options() == null ? Map.of() : widget.options());
        return new DashboardWidget(query.id(), chart.value(), query.settle(widget.options() == null
                ? Map.of() : widget.options()), title, widget.size());
    }

    private static DashboardScope scopeOf(UUID projectId) {
        return projectId == null ? DashboardScope.WORKSPACE : DashboardScope.PROJECT;
    }

    private static DashboardResponse toResponse(Dashboard dashboard, UUID userId) {
        return new DashboardResponse(dashboard.getId(), dashboard.getName(), dashboard.isShared(),
                dashboard.isOwnedBy(userId),
                dashboard.getWidgets().stream().map(widget -> new DashboardWidgetDto(widget.queryId(), widget.chart(),
                        widget.options(), widget.title(), widget.size())).toList(),
                dashboard.getVersion(), dashboard.getUpdatedAt());
    }
}
