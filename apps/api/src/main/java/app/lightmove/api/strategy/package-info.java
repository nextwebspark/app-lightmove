/**
 * <b>Strategy — the market side.</b> Finding companies: the filter a mandate saves, the searches
 * saved beside it, and the reads over the Apollo company universe. The vocabulary a search is
 * expressed in — bands, sectors, market segments, facets, the sort allowlist — belongs here too,
 * because it describes the market rather than any one mandate.
 *
 * <p>{@code triagecompany} reads this package's scope to bulk-add from it. The one exception to
 * "never the reverse": the Strategy search excludes a project's already-triaged companies, so they
 * stop reappearing once filed. That needs a predicate only {@code triagecompany} can build — it owns
 * the table the exclusion tests — bridged through
 * {@link app.lightmove.api.strategy.service.TriagedCompanyLookup} — an interface this package owns
 * and declares, answered as an opaque {@link app.lightmove.api.strategy.model.CompanyExclusion} this
 * package composes into its own query without ever naming {@code triagecompany}'s table. The
 * compile-time dependency stays one-way; only a bean satisfying the interface crosses back.
 */
package app.lightmove.api.strategy;
