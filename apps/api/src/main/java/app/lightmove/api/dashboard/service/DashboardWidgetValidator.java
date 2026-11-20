package app.lightmove.api.dashboard.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.dashboard.constant.ChartType;
import app.lightmove.api.dashboard.constant.DashboardScope;
import app.lightmove.api.dashboard.constant.WidgetSize;
import app.lightmove.api.dashboard.dto.DashboardWidgetDto;
import app.lightmove.api.dashboard.model.ChartQuery;
import app.lightmove.api.dashboard.model.Dashboard;
import app.lightmove.api.dashboard.model.DashboardWidget;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Every chart a save sends, checked against the library: the query on this kind of dashboard, a chart type its shape
 * allows, its own options, a known size. A chart whose query has since left the library is kept exactly as it was
 * stored — never as the request now spells it — so it can still be removed, and nothing new can arrive that way.
 */
@Component
@RequiredArgsConstructor
class DashboardWidgetValidator {

    private final ChartQueryLibrary library;

    List<DashboardWidget> checked(DashboardScope scope, List<DashboardWidgetDto> sent, List<DashboardWidget> stored) {
        if (sent.size() > Dashboard.MAX_WIDGETS) {
            throw ApiException.of(ErrorCode.DASHBOARD_TOO_MANY_WIDGETS);
        }
        Map<String, DashboardWidget> storedByQueryId = stored.stream().collect(Collectors.toMap(
                DashboardWidget::queryId, Function.identity(), (first, later) -> first, LinkedHashMap::new));
        return sent.stream().map(widget -> checked(scope, widget, storedByQueryId)).toList();
    }

    /** The charts of {@code source} whose queries are still in the library, for a copy that starts clean. */
    List<DashboardWidget> stillAvailable(DashboardScope scope, List<DashboardWidget> source) {
        return source.stream().filter(widget -> library.find(scope, widget.queryId()).isPresent()).toList();
    }

    private DashboardWidget checked(DashboardScope scope, DashboardWidgetDto widget,
                                    Map<String, DashboardWidget> storedByQueryId) {
        Optional<ChartQuery> found = library.find(scope, widget.queryId());
        if (found.isEmpty()) {
            DashboardWidget retired = storedByQueryId.get(widget.queryId());
            if (retired == null) {
                throw new ApiException(ErrorCode.CHART_QUERY_NOT_FOUND, "No library query " + widget.queryId());
            }
            return retired;
        }
        ChartQuery query = found.get();
        if (WidgetSize.fromValue(widget.size()) == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown widget size " + widget.size());
        }
        ChartType chart = ChartType.fromValue(widget.chart());
        if (chart == null || !ChartRules.allowedBeforeData(query.shape(), query.notAdditive()).contains(chart)) {
            throw new ApiException(ErrorCode.CHART_TYPE_NOT_ALLOWED,
                    "Query " + query.id() + " cannot be drawn as " + widget.chart());
        }
        Map<String, String> options = widget.options() == null ? Map.of() : widget.options();
        ChartQueryService.requireValidOptions(query, options);
        String title = widget.title() == null || widget.title().isBlank() ? null : widget.title().strip();
        return new DashboardWidget(query.id(), chart.value(), query.settle(options), title, widget.size());
    }
}
