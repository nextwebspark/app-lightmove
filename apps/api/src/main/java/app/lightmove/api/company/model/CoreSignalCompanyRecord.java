package app.lightmove.api.company.model;

import java.math.BigDecimal;

/**
 * One company as parsed from a CoreSignal collect response — the internal shape handed from the
 * HTTP client to the cache. Every field except the id and name is optional: CoreSignal's field
 * population varies wildly by company, and a sparse record is still worth caching (it was paid
 * for). {@code rawPayload} is the collect response verbatim, kept because re-extracting fields
 * from stored JSON is free while re-collecting costs credits.
 */
public record CoreSignalCompanyRecord(
        long coresignalId,
        String name,
        String website,
        String linkedinUrl,
        String description,
        String industry,
        Integer employeesCount,
        String sizeRange,
        BigDecimal revenueAnnual,
        String revenueRange,
        String hqLocation,
        String hqCountry,
        String hqCountryIso2,
        Integer foundedYear,
        String logoUrl,
        String rawPayload
) {}
