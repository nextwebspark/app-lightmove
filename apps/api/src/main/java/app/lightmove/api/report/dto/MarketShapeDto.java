package app.lightmove.api.report.dto;

import java.util.List;

/**
 * Chapter two. {@code cells} holds every sector × level pair, zeros included; executives with no
 * sector or seniority are counted apart, in no cell. {@code hubs} are the leading countries.
 */
public record MarketShapeDto(
        List<String> sectors,
        List<String> levels,
        List<MarketCellDto> cells,
        int withoutSector,
        int withoutSeniority,
        List<MarketSliceDto> slices,
        List<TalentHubDto> hubs,
        int elsewhere,
        int unlocated,
        List<BreakdownDto> companiesBySector
) {}
