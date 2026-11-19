package app.lightmove.api.strategy.dto;

import app.lightmove.api.universe.dto.CompanyResultDto;
import java.util.List;

/** One page of the Strategy results; {@code totalCount} is over the whole filter, not the page. */
public record StrategyCompaniesResponse(List<CompanyResultDto> companies, long totalCount,
                                         int page, int size) {}
