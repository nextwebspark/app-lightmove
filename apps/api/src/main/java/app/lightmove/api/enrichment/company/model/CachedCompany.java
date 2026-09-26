package app.lightmove.api.enrichment.company.model;

import java.time.Instant;
import java.util.Optional;

/**
 * One remembered provider answer, or a stored miss. A plain record: {@code CachedCompanyStore}
 * replaces the row whole by upsert.
 */
public record CachedCompany(String provider, Instant fetchedAt, VendorCompanyRecord answer) {

    /** Empty for a stored miss: the provider was asked about this slug and had no record. */
    public Optional<VendorCompanyRecord> found() {
        return Optional.ofNullable(answer);
    }
}
