package app.lightmove.api.strategy.dto;

import java.util.List;

/**
 * Everything the Strategy screen needs before it draws. The universe's facet counts are the same for
 * every mandate, so they are a separate read.
 */
public record StrategyResponse(StrategyFilterDto filter, List<CompanyRefDto> offLimits,
                                List<SavedSearchResponse> searches) {}
