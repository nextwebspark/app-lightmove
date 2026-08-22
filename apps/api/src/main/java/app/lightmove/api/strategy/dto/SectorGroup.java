package app.lightmove.api.strategy.dto;

import java.util.List;

/**
 * One group of the sector taxonomy with its industries.
 *
 * <p>{@code count} is the rolled-up total across {@code industries}, so the group header can state
 * the size of the whole slice without the client summing the children — and so a group whose
 * industries are all absent from the universe can be recognised as empty rather than rendered blank.
 */
public record SectorGroup(String name, long count, List<FacetCount> industries) {}
