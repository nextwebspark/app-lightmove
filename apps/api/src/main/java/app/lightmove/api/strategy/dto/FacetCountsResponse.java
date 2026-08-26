package app.lightmove.api.strategy.dto;

import java.util.Map;

/**
 * How many companies each filter option still reaches under a mandate's current selection, keyed by
 * the token a saved filter stores.
 *
 * <p>Counts only — no labels and no order. Both come from {@link FacetsResponse}, which describes the
 * market itself and is the same for every mandate; this response is the one thing about the sidebar
 * that a chip click changes. Splitting them that way is what keeps a row from re-ranking itself
 * under the hand that just clicked the row above it.
 *
 * <p><b>Each axis is counted with every criterion applied except its own.</b> Picking a country
 * recounts the industries under it, and leaves the other countries counting under the remaining axes
 * — the alternative reads zero for every country but the chosen one, which makes the accordion
 * useless the moment it is used.
 *
 * <p><b>An option absent from a map counts zero.</b> The vocabularies are the other response's, and
 * the client renders from them, so repeating 148 industries here to carry a zero would only give the
 * two reads a way to disagree about what exists.
 *
 * <p>{@code marketSegments} overlaps by design — a company can be B2B and SaaS at once — so its
 * counts sum to more than the scope. Every other axis partitions it.
 */
public record FacetCountsResponse(Map<String, Long> industries, Map<String, Long> countries,
                                  Map<String, Long> employeeBands, Map<String, Long> revenueBands,
                                  Map<String, Long> marketSegments) {}
