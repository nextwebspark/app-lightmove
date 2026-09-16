package app.lightmove.api.report.dto;

import java.util.List;

/**
 * Chapter two: where the mapped executives sit, by sector and seniority and by country.
 *
 * <p>{@code sectors} is the leading sectors plus "Other" for the tail; {@code cells} holds one count
 * for every sector × level pair, zeros included, so the matrix needs no filling in. An executive
 * mapped at no company of the universe has no sector and is counted in {@code withoutSector}; one
 * with no seniority in {@code withoutSeniority}. Neither sits in a cell.
 *
 * <p>{@code hubs} is the leading countries by headcount, {@code elsewhere} everyone in a country past
 * that cut, and {@code unlocated} everyone with no country on file.
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
