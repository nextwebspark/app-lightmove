package app.lightmove.api.enrichment.company.model;

import java.time.Instant;
import java.util.Optional;

/**
 * One remembered answer: what a provider said about a slug, or that it had nothing to say.
 *
 * <p>A plain record rather than an entity — the row is replaced whole by {@code CachedCompanyStore}'s
 * upsert, so it carries no version and no updated-at, and it holds a {@code jsonb} and a
 * {@code text[]} that JDBC reads more honestly than a mapping would.
 */
public record CachedCompany(String provider, Instant fetchedAt, VendorCompanyRecord answer) {

    /** Empty for a stored miss: the provider was asked about this slug and had no record. */
    public Optional<VendorCompanyRecord> found() {
        return Optional.ofNullable(answer);
    }
}
