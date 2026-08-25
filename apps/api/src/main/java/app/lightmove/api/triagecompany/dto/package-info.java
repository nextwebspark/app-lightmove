/**
 * The HTTP contract for a mandate's triaged companies: taking one out of the market, capturing one the
 * market does not carry, moving it between stages, reading a stage back a page at a time, and removing
 * a company from the mandate.
 *
 * <p>The two writes that create a row are deliberately separate endpoints, because their trust models
 * are opposites. {@link app.lightmove.api.triagecompany.dto.AddTriageCompanyRequest} names a company by
 * {@code apolloAccountId} and nothing else — the snapshot is resolved server-side, so a caller cannot
 * file a market company under a name of its own choosing.
 * {@link app.lightmove.api.triagecompany.dto.CaptureCompanyRequest} carries the fields itself, because
 * there is no universe row to resolve them from; the row records that provenance rather than pretending
 * the two are the same.
 *
 * <p>Reads are project-scoped and seat-gated; the shape of the market itself is a different contract, in
 * {@link app.lightmove.api.strategy.dto}.
 */
package app.lightmove.api.triagecompany.dto;
