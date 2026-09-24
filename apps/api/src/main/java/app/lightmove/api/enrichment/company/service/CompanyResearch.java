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
 * What a provider says about a LinkedIn company page: from the cache where it can, from the vendor
 * where it must, and remembered either way.
 *
 * <p>The same company captured in two mandates used to be bought twice — the capture event fires per
 * mandate and nothing held the answer. A cached row is a fact about a public page rather than any
 * firm's research, which is what lets one table answer for every workspace; see V64's header.
 *
 * <p>Deliberately not {@code @Transactional} — the read and write each open their own in
 * {@link CachedCompanyStore}, and the vendor call sits between them in none.
 */
@Service
public class CompanyResearch {

    /** Smallest page a name search in one country counts — below it, a namesake is likelier than a match. */
    public static final int MIN_EMPLOYEES_IN_COUNTRY = 50;

    /** Smallest page counted as a global company's own; a name as short as "H&M" otherwise buys strangers. */
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

        // Nothing is asked and nothing remembered with enrichment off: "no record" is not a finding
        // there, and a stored miss would outlive the day someone configures a key by the whole TTL.
        if (!enricher.isEnabled()) {
            return Optional.empty();
        }

        Optional<VendorCompanyRecord> answer = enricher.fetch(linkedinSlug);
        // A vendor that threw never reaches here, so a bad minute is not remembered as a miss.
        store.remember(linkedinSlug, enricher.provider(), answer);
        return answer.flatMap(VendorCompanyRecord::asCapturedDetails);
    }

    /**
     * A company someone named, found by its page's name in one country. Every hit a search returns
     * is remembered under its own slug, because every hit is billed: an "Aldar" search pays for Aldar
     * Education too, and a later ask for it is a cache read. A name nothing matches is not
     * remembered — there is no slug to key it on. A search {@code allowance} has no room for is not
     * made.
     */
    public Optional<CapturedCompanyDetails> byName(String name, String country, VendorSearchAllowance allowance) {
        String countryCode = Countries.codeOf(country);
        return countryCode == null ? Optional.empty()
                : named(name, Countries.nameOf(country), countryCode, MIN_EMPLOYEES_IN_COUNTRY, allowance);
    }

    /**
     * A global company's own page, wherever it is headquartered — IKEA is Swedish however local the
     * ask. Only a big one counts: a small namesake abroad is somebody else.
     */
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
