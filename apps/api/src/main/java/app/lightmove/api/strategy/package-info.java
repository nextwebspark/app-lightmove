/**
 * <b>Strategy — the market side.</b> The filter a mandate saves, the searches saved beside it, the
 * reads over the Apollo universe, and the vocabulary a search is expressed in. {@code triagecompany}
 * depends on this package, never the reverse: the search's exclusion of already-triaged companies
 * crosses back only as a bean implementing
 * {@link app.lightmove.api.strategy.service.TriagedCompanyLookup}, which this package owns.
 */
package app.lightmove.api.strategy;
