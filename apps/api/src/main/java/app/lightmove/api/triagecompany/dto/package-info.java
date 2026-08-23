/**
 * The HTTP contract for a mandate's triaged companies: adding one, moving it between stages, and
 * reading a stage back a page at a time.
 *
 * <p>Every write names a company by {@code apolloAccountId} and nothing else — the snapshot is
 * resolved server-side, so a caller cannot file a company under a name of its own choosing. Reads are
 * project-scoped and seat-gated; the shape of the market itself is a different contract, in
 * {@link app.lightmove.api.strategy.dto}.
 */
package app.lightmove.api.triagecompany.dto;
