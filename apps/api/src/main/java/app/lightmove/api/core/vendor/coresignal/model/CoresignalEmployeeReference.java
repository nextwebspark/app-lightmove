package app.lightmove.api.core.vendor.coresignal.model;

/**
 * One person Coresignal says works at a company we asked about — as our type, not theirs.
 *
 * <p>A reference and nothing more, because a search is all this layer's example performs: Coresignal
 * answers a filter with identifiers, and turning an identifier into a name, a title and a career
 * history is a second, separately-priced call. Modelling a full profile here would mean inventing
 * fields nothing populates.
 *
 * <p>The id is stored rather than a profile URL for the reason the sourcing design gives: they track
 * a person across URL changes, so keying on the URL forks the record the day somebody renames.
 */
public record CoresignalEmployeeReference(long coresignalEmployeeId) {
}
