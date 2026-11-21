package app.lightmove.api.dashboard.dto;

/**
 * One chart's answer, in the order asked: {@code result}, or the {@code code} of why it has none
 * ({@code CHART_QUERY_NOT_FOUND} for a retired query, {@code CHART_OPTION_INVALID}) so one chart never fails the rest.
 */
public record ChartAnswerDto(String queryId, ChartResultResponse result, String code) {}
