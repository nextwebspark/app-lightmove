package app.lightmove.api.dashboard.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.dashboard.constant.ChartType;
import app.lightmove.api.dashboard.constant.DashboardScope;
import app.lightmove.api.dashboard.dto.ChartAnswerDto;
import app.lightmove.api.dashboard.dto.ChartOptionDto;
import app.lightmove.api.dashboard.dto.ChartQueryResponse;
import app.lightmove.api.dashboard.dto.ChartQuestionDto;
import app.lightmove.api.dashboard.dto.ChartResultResponse;
import app.lightmove.api.dashboard.model.ChartOption;
import app.lightmove.api.dashboard.model.ChartQuery;
import app.lightmove.api.dashboard.model.ChartQueryContext;
import app.lightmove.api.dashboard.model.ChartResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        return answer(context, require(scope, queryId), asked == null ? Map.of() : asked);
    }

    /**
     * Every chart of a dashboard in one request and one transaction, so the reads they share — the report, the
     * executives, the positions — are made once ({@code ChartQueryContext.sharedRead}) rather than once a chart.
     */
    @Transactional(readOnly = true)
    public List<ChartAnswerDto> answers(ChartQueryContext context, DashboardScope scope,
                                        List<ChartQuestionDto> questions) {
        List<ChartAnswerDto> answers = new ArrayList<>();
        for (ChartQuestionDto question : questions) {
            Optional<ChartQuery> query = library.find(scope, question.queryId());
            Map<String, String> asked = question.options() == null ? Map.of() : question.options();
            if (query.isEmpty()) {
                answers.add(new ChartAnswerDto(question.queryId(), null, ErrorCode.CHART_QUERY_NOT_FOUND.name()));
            } else if (!hasValidOptions(query.get(), asked)) {
                answers.add(new ChartAnswerDto(question.queryId(), null, ErrorCode.CHART_OPTION_INVALID.name()));
            } else {
                answers.add(new ChartAnswerDto(question.queryId(), answer(context, query.get(), asked), null));
            }
        }
        return answers;
    }

    private ChartResultResponse answer(ChartQueryContext context, ChartQuery query, Map<String, String> requested) {
        requireValidOptions(query, requested);
        Map<String, String> options = query.settle(requested);
        ChartResult result = ChartShaping.shape(query, query.runner().run(context, options), options);
        List<String> allowed = ChartRules.allowed(result.shape(), query.notAdditive(), result.labels().size()).stream()
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
        if (!hasValidOptions(query, requested)) {
            throw new ApiException(ErrorCode.CHART_OPTION_INVALID,
                    "Query " + query.id() + " was asked options it does not offer: " + requested.keySet());
        }
    }

    private static boolean hasValidOptions(ChartQuery query, Map<String, String> requested) {
        return requested.entrySet().stream().allMatch(entry -> query.options().stream().anyMatch(option ->
                option.key().key().equals(entry.getKey()) && option.allows(entry.getValue())));
    }

    private static ChartQueryResponse toResponse(ChartQuery query) {
        return new ChartQueryResponse(query.id(), query.category(), query.label(), query.description(),
                query.shape().name(), query.notAdditive(), query.defaultChart().value(),
                ChartRules.allowedBeforeData(query.shape(), query.notAdditive()).stream().map(ChartType::value).toList(),
                query.options().stream().map(ChartQueryService::toOption).toList());
    }

    private static ChartOptionDto toOption(ChartOption option) {
        return new ChartOptionDto(option.key().key(), option.label(), option.choices(), option.defaultValue());
    }
}
