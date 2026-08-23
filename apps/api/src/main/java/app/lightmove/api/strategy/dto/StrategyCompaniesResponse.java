package app.lightmove.api.strategy.dto;

import java.util.List;

/**
 * One page of the Strategy screen's results — the universe narrowed by the mandate's saved filter.
 *
 * <p>{@code totalCount} is the count over the same filter, not over the page, because it is what the
 * pagination bar states and what "Add all to Universe" is about to act on.
 */
public record StrategyCompaniesResponse(List<CompanyResultDto> companies, long totalCount,
                                         int page, int size) {}
