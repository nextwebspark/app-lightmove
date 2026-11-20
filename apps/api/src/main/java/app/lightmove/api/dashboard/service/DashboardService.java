package app.lightmove.api.dashboard.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.dashboard.constant.DashboardScope;
import app.lightmove.api.dashboard.dto.DashboardResponse;
import app.lightmove.api.dashboard.dto.DashboardWidgetDto;
import app.lightmove.api.dashboard.dto.SaveDashboardRequest;
import app.lightmove.api.dashboard.model.Dashboard;
import app.lightmove.api.dashboard.model.DashboardWidget;
import app.lightmove.api.dashboard.repository.DashboardRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Members' dashboards on a position ({@code projectId}) or across the workspace (null). A dashboard is its owner's;
 * shared, the rest of the staff who can reach its scope open it read-only. Every chart on it is checked against the
 * library on save ({@code DashboardWidgetValidator}).
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardRepository dashboards;
    private final DashboardWidgetValidator widgets;

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
        dashboard.record(request.name().strip(), request.shared(),
                widgets.checked(scopeOf(projectId), request.widgets(), List.of()));
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
                widgets.checked(scopeOf(projectId), request.widgets(), dashboard.getWidgets()));
        return toResponse(dashboards.saveAndFlush(dashboard), userId);
    }

    /**
     * A private copy of a dashboard the caller may open — theirs or a teammate's shared one — without any chart whose
     * query has left the library, so the copy is something they can save.
     */
    @Transactional
    public DashboardResponse duplicate(UUID userId, UUID workspaceId, UUID projectId, UUID dashboardId) {
        Dashboard source = requireVisible(userId, workspaceId, projectId, dashboardId);
        String name = (source.getName() + " (copy)");
        Dashboard copy = Dashboard.startFor(workspaceId, projectId, userId);
        copy.record(name.length() > Dashboard.MAX_NAME ? name.substring(0, Dashboard.MAX_NAME) : name, false,
                widgets.stillAvailable(scopeOf(projectId), source.getWidgets()));
        return toResponse(dashboards.save(copy), userId);
    }

    @Transactional
    public void delete(UUID userId, UUID workspaceId, UUID projectId, UUID dashboardId) {
        dashboards.delete(requireOwned(userId, workspaceId, projectId, dashboardId));
    }

    private Dashboard requireVisible(UUID userId, UUID workspaceId, UUID projectId, UUID dashboardId) {
        return dashboards.findByIdAndWorkspaceId(dashboardId, workspaceId)
                .filter(found -> Objects.equals(found.getProjectId(), projectId))
                .filter(found -> found.isOwnedBy(userId) || found.isShared())
                .orElseThrow(() -> ApiException.of(ErrorCode.DASHBOARD_NOT_FOUND));
    }

    private Dashboard requireOwned(UUID userId, UUID workspaceId, UUID projectId, UUID dashboardId) {
        Dashboard dashboard = requireVisible(userId, workspaceId, projectId, dashboardId);
        if (!dashboard.isOwnedBy(userId)) {
            throw ApiException.of(ErrorCode.DASHBOARD_READ_ONLY);
        }
        return dashboard;
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
