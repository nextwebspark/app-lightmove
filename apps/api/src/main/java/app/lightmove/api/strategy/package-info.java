/**
 * <b>Strategy — the market side.</b> Finding companies: the filter a mandate saves, the searches
 * saved beside it, and the reads over the Apollo company universe. The vocabulary a search is
 * expressed in — bands, sectors, market segments, facets, the sort allowlist — belongs here too,
 * because it describes the market rather than any one mandate.
 *
 * <p>{@code triagecompany} reads this package's scope to bulk-add from it; strategy never looks at a
 * mandate's universe.
 */
package app.lightmove.api.strategy;
