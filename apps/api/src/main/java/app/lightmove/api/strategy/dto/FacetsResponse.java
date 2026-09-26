package app.lightmove.api.strategy.dto;

import java.util.List;
import java.util.Map;

/**
 * The Strategy sidebar in one read, identical for every project in the workspace. Market-segment
 * counts overlap; every other facet partitions the universe.
 */
public record FacetsResponse(List<SectorGroup> sectorGroups,
                             Map<String, List<String>> adjacentIndustries,
                             List<FacetCount> marketSegments,
                             List<FacetCount> employeeBands, List<FacetCount> revenueBands) {}
