package app.lightmove.api.enrichment.company.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.enrichment.company.model.CachedCompany;
import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.time.Duration;
import java.time.Instant;
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
}
