package app.lightmove.api.dashboard.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.dashboard.constant.ChartType;
import app.lightmove.api.dashboard.constant.DashboardScope;
import app.lightmove.api.dashboard.dto.ChartOptionDto;
import app.lightmove.api.dashboard.dto.ChartQueryResponse;
import app.lightmove.api.dashboard.dto.ChartResultResponse;
import app.lightmove.api.dashboard.model.ChartOption;
import app.lightmove.api.dashboard.model.ChartQuery;
import app.lightmove.api.dashboard.model.ChartQueryContext;
import app.lightmove.api.dashboard.model.ChartResult;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lists the library and answers one of its queries. Options are checked against the query's own declaration. */
@Service
@RequiredArgsConstructor
public class ChartQueryService {

    private final ChartQueryLibrary library;

    public List<ChartQueryResponse> library(DashboardScope scope) {
        return library.of(scope).stream().map(ChartQueryService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ChartResultResponse run(ChartQueryContext context, DashboardScope scope, String queryId,
                                   Map<String, String> asked) {
        ChartQuery query = require(scope, queryId);
        Map<String, String> requested = asked == null ? Map.of() : asked;
        requireValidOptions(query, requested);
        Map<String, String> options = query.settle(requested);
        ChartResult result = ChartShaping.shape(query, query.runner().run(context, options), options);
        List<String> allowed = ChartRules.allowed(result.shape(), query.noSum(), result.labels().size()).stream()
                .map(ChartType::value).toList();
        return new ChartResultResponse(query.id(), result.shape().name(), allowed, options, result.dimension(),
                result.measure(), result.unit(), result.labels(), result.values(), result.series(),
                result.suppressed(), result.kpi(), result.columns(), result.rows(), result.note(), result.percent(),
                result.truncated());
    }

    ChartQuery require(DashboardScope scope, String queryId) {
        return library.find(scope, queryId).orElseThrow(() -> ApiException.of(ErrorCode.CHART_QUERY_NOT_FOUND));
    }

    static void requireValidOptions(ChartQuery query, Map<String, String> requested) {
        for (Map.Entry<String, String> entry : requested.entrySet()) {
            boolean allowed = query.options().stream().anyMatch(option ->
                    option.key().key().equals(entry.getKey()) && option.allows(entry.getValue()));
            if (!allowed) {
                throw new ApiException(ErrorCode.CHART_OPTION_INVALID,
                        "Query " + query.id() + " has no option " + entry.getKey() + "=" + entry.getValue());
            }
        }
    }

    private static ChartQueryResponse toResponse(ChartQuery query) {
        return new ChartQueryResponse(query.id(), query.category(), query.label(), query.description(),
                query.shape().name(), query.noSum(), query.staffOnly(), query.defaultChart().value(),
                ChartRules.allowedBeforeData(query.shape(), query.noSum()).stream().map(ChartType::value).toList(),
                query.options().stream().map(ChartQueryService::toOption).toList());
    }

    private static ChartOptionDto toOption(ChartOption option) {
        return new ChartOptionDto(option.key().key(), option.label(), option.choices(), option.defaultValue());
    }
}
