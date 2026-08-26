package app.lightmove.api.strategy.dto;

import java.util.List;
import java.util.Map;

/**
 * Everything the Strategy filter sidebar renders, in one read.
 *
 * <p>One call rather than five because the sidebar shows five accordions at once and none of them
 * depends on the others — splitting it would mean five round trips to draw one panel. Everything
 * here is the market's own shape, so this response is the same for every project in the workspace
 * and stays valid until the pipeline next loads, which is what makes it worth caching.
 *
 * <p><b>The counts here are the universe's, not the selection's.</b> They order the accordions and
 * stand in until a mandate's own counts arrive; {@link FacetCountsResponse} carries the numbers a
 * row settles on. Keeping the order here is the point of the split — a row ranked by a number that
 * moves would slide out from under the hand that just clicked the row above it.
 *
 * <p>Each entry carries its {@code value} — the token a saved filter stores — beside its display
 * {@code label}, so the client never keeps a mirror of the band vocabulary and never has to guess
 * which of the two a filter should hold.
 *
 * <p>{@code countries} is the one axis that arrives uncounted, as {@link FacetValue}: six GCC pills
 * whose shape is the information, ordered by size but carrying no number. See {@code FacetValue}.
 *
 * <p>{@code marketSegments} counts overlap by design — a company can be B2B and SaaS at once — so they
 * sum to more than the universe. Every other facet's counts partition it.
 *
 * <p>{@code adjacentIndustries} is advice rather than a facet: which industries sit beside which,
 * for the panel's suggestion chips. It carries no counts because it selects nothing on its own.
 *
 * <p>There is no ownership facet: the universe has no ownership column.
 */
public record FacetsResponse(List<SectorGroup> sectorGroups,
                             Map<String, List<String>> adjacentIndustries,
                             List<FacetCount> marketSegments, List<FacetValue> countries,
                             List<FacetCount> employeeBands, List<FacetCount> revenueBands) {}
