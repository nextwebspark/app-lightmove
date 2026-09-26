package app.lightmove.api.strategy.dto;

/**
 * One selectable value in a filter accordion. The count is over the whole universe, not the rest of
 * the current selection: the chip asks "how big is this slice of the market".
 */
public record FacetCount(String value, String label, long count) {}
