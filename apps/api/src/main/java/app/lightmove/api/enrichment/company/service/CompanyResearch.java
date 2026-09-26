package app.lightmove.api.enrichment.company.service;

import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.enrichment.company.model.CachedCompany;
import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import app.lightmove.api.enrichment.company.model.VendorSearchAllowance;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * A provider's answer about a LinkedIn company page — cached, fetched, and remembered either way, so
 * one company captured in two mandates is bought once. Not {@code @Transactional}: the vendor call
 * sits between {@link CachedCompanyStore}'s own read and write transactions.
 */
@Service
public class CompanyResearch {

    /** Below this, a namesake is likelier than a match. */
    public static final int MIN_EMPLOYEES_IN_COUNTRY = 50;

    /** A name as short as "H&M" otherwise buys strangers. */
    public static final int MIN_EMPLOYEES_ANYWHERE = 1_000;

    private final LinkedInCompanyEnricher enricher;
    private final CachedCompanyStore store;
    private final Duration cacheTtl;

    public CompanyResearch(LinkedInCompanyEnricher enricher, CachedCompanyStore store,
                           LightMoveProperties properties) {
        this.enricher = enricher;
        this.store = store;
        this.cacheTtl = properties.enrichment().companyCacheTtl();
    }

    public Optional<CapturedCompanyDetails> of(String linkedinSlug) {
        Instant staleBefore = Instant.now().minus(cacheTtl);
        Optional<CachedCompany> held = store.find(linkedinSlug);
        if (held.isPresent() && held.get().fetchedAt().isAfter(staleBefore)) {
            return held.get().found().flatMap(VendorCompanyRecord::asCapturedDetails);
        }

        // With enrichment off a stored miss would outlive the day someone configures a key.
        if (!enricher.isEnabled()) {
            return Optional.empty();
        }

        Optional<VendorCompanyRecord> answer = enricher.fetch(linkedinSlug);
        // A vendor that threw never reaches here, so a bad minute is not remembered as a miss.
        store.remember(linkedinSlug, enricher.provider(), answer);
        return answer.flatMap(VendorCompanyRecord::asCapturedDetails);
    }

    /**
     * Every hit is remembered under its own slug, because every hit is billed. A name nothing matches is
     * not remembered, and a search the {@code allowance} has no room for is not made.
     */
    public Optional<CapturedCompanyDetails> byName(String name, String country, VendorSearchAllowance allowance) {
        String countryCode = Countries.codeOf(country);
        return countryCode == null ? Optional.empty()
                : named(name, Countries.nameOf(country), countryCode, MIN_EMPLOYEES_IN_COUNTRY, allowance);
    }

    /** Only a big company counts abroad: a small namesake is somebody else. */
    public Optional<CapturedCompanyDetails> byNameAnywhere(String name, VendorSearchAllowance allowance) {
        return named(name, null, null, MIN_EMPLOYEES_ANYWHERE, allowance);
    }

    private Optional<CapturedCompanyDetails> named(String name, String countryName, String countryCode,
                                                   int minEmployees, VendorSearchAllowance allowance) {
        Optional<VendorCompanyRecord> held = store.findByName(CompanyNames.spellingsOf(name),
                CompanyNames.matchKeys(name), countryName, minEmployees, Instant.now().minus(cacheTtl));
        if (held.isPresent()) {
            return held.flatMap(VendorCompanyRecord::asCapturedDetails);
        }
        if (!enricher.isEnabled()) {
            return Optional.empty();
        }
        for (String term : CompanyNames.searchTerms(name)) {
            if (!allowance.take()) {
                return Optional.empty();
            }
            List<VendorCompanyRecord> hits = enricher.searchByName(term, countryCode, minEmployees);
            store.rememberAll(enricher.provider(), hits);
            Optional<VendorCompanyRecord> named = CompanyNames.best(name, hits);
            if (named.isPresent()) {
                return named.flatMap(VendorCompanyRecord::asCapturedDetails);
            }
        }
        return Optional.empty();
    }
}
