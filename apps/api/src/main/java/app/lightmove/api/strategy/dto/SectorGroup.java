package app.lightmove.api.strategy.dto;

import java.util.List;

/**
 * One group of the sector taxonomy with its industries.
 *
 * <p>No rolled-up total: the sidebar states a group by what it contains, not by its size, and a
 * count on the group header was read as "how much of this is selected" rather than "how big this
 * slice is".
 */
public record SectorGroup(String name, List<FacetCount> industries) {}
