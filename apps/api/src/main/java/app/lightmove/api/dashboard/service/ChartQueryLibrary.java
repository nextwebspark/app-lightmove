package app.lightmove.api.dashboard.service;

import app.lightmove.api.dashboard.constant.DashboardScope;
import app.lightmove.api.dashboard.model.ChartQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Every ready-made chart, by id. The library is code: a query exists because someone wrote and reviewed it. */
@Component
public class ChartQueryLibrary {

    private final Map<String, ChartQuery> byId = new LinkedHashMap<>();

    public ChartQueryLibrary(ProjectChartQueries projectQueries, WorkspaceChartQueries workspaceQueries) {
        for (ChartQuery query : projectQueries.queries()) {
            register(query);
        }
        for (ChartQuery query : workspaceQueries.queries()) {
            register(query);
        }
    }

    private void register(ChartQuery query) {
        if (byId.putIfAbsent(query.id(), query) != null) {
            throw new IllegalStateException("Two library queries share the id " + query.id());
        }
    }

    public List<ChartQuery> of(DashboardScope scope) {
        return byId.values().stream().filter(query -> query.scope() == scope).toList();
    }

    public Optional<ChartQuery> find(DashboardScope scope, String id) {
        return Optional.ofNullable(id == null ? null : byId.get(id)).filter(query -> query.scope() == scope);
    }
}
