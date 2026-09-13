package app.lightmove.api.report.dto;

import java.util.List;

/**
 * What one non-empty cell of the matrix holds: the companies contributing, most executives first,
 * and the executives themselves up to {@code lightmove.report.max-executives-per-slice}.
 */
public record MarketSliceDto(String sector, String level, List<String> companies,
                             List<SliceExecutiveDto> executives) {}
