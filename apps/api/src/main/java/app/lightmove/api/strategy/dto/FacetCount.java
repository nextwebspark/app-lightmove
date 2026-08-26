package app.lightmove.api.strategy.dto;

/**
 * One selectable value in a filter accordion: the token a filter stores, the chip's label, and how
 * many companies carry it across the <i>whole</i> universe.
 *
 * <p>That total is the market's own size, and it is what orders the accordion — an order taken from
 * a number that moved with the selection would re-rank the rows under the hand that just clicked
 * one. The number a row actually shows is the selection's, and it travels separately in
 * {@link FacetCountsResponse}.
 */
public record FacetCount(String value, String label, long count) {}
