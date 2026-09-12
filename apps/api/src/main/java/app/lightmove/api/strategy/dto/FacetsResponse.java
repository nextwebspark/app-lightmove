package app.lightmove.api.strategy.dto;

import java.util.List;
import java.util.Map;

/**
 * Everything the Strategy filter sidebar renders, in one read. The counts are over the whole
 * universe, so this response is the same for every project in the workspace. Each entry carries the
 * {@code value} a saved filter stores beside its display {@code label}.
 *
 * <p>{@code marketSegments} counts overlap by design — a company can be B2B and SaaS at once — so
 * they sum to more than the universe. Every other facet's counts partition it.
 * {@code adjacentIndustries} carries no counts because it selects nothing on its own.
 */
public record FacetsResponse(List<SectorGroup> sectorGroups,
                             Map<String, List<String>> adjacentIndustries,
                             List<FacetCount> marketSegments,
                             List<FacetCount> employeeBands, List<FacetCount> revenueBands) {}
