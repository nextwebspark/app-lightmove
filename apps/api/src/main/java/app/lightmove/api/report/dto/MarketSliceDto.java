package app.lightmove.api.report.dto;

import java.util.List;

/** One non-empty matrix cell: its companies, most executives first, and executives up to the per-slice cap. */
public record MarketSliceDto(String sector, String level, List<String> companies,
                             List<SliceExecutiveDto> executives) {}
