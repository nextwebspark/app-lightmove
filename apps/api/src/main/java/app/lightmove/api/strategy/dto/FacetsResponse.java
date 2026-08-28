package app.lightmove.api.strategy.dto;

import java.util.List;
import java.util.Map;

/**
 * Everything the Strategy filter sidebar renders, in one read.
 *
 * <p>One call rather than five because the sidebar shows five accordions at once and none of the
 * counts depends on the others — splitting it would mean five round trips to draw one panel. The
 * counts are over the whole universe, so this response is the same for every project in the
 * workspace and stays valid until the pipeline next loads.
 *
 * <p>Each entry carries its {@code value} — the token a saved filter stores — beside its display
 * {@code label}, so the client never keeps a mirror of the band vocabulary and never has to guess
 * which of the two a filter should hold.
 *
 * <p>{@code marketSegments} counts overlap by design — a company can be B2B and SaaS at once — so they
 * sum to more than the universe. Every other facet's counts partition it.
 *
 * <p>{@code adjacentIndustries} is advice rather than a facet: which industries sit beside which,
 * for the panel's suggestion chips. It carries no counts because it selects nothing on its own.
 *
 * <p>There is no ownership facet: the universe has no ownership column, and no location facet: the
 * six GCC markets are a fixed vocabulary the sidebar holds, so counting them cost a GROUP BY over the
 * whole universe to draw six chips whose counts decided nothing.
 */
public record FacetsResponse(List<SectorGroup> sectorGroups,
                             Map<String, List<String>> adjacentIndustries,
                             List<FacetCount> marketSegments,
                             List<FacetCount> employeeBands, List<FacetCount> revenueBands) {}
