package app.lightmove.api.strategy.dto;

/**
 * One selectable value in a filter accordion: the token a filter stores, the chip's label, and how
 * many companies carry it.
 *
 * <p>The count is taken over the <i>whole</i> universe, not over the rest of the current selection.
 * That is the mockup's own behaviour, and it is the more useful one here: a count that shrank as you
 * selected would answer "how many are left" when the question the chip is asking is "how big is this
 * slice of the market".
 */
public record FacetCount(String value, String label, long count) {}
