package app.lightmove.api.strategy.dto;

import java.util.List;

/**
 * One sector group with its industries. No rolled-up total: a header count read as "how much is
 * selected".
 */
public record SectorGroup(String name, List<FacetCount> industries) {}
