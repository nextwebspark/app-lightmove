package app.lightmove.api.enrichment.company.service;

import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Answers every company research request with nothing — the default, so a fresh clone runs the whole
 * capture flow with no vendor account and captured rows keep what the plugin read.
 */
@Slf4j
public class LogCompanyEnricher implements LinkedInCompanyEnricher {

    @Override
    public Optional<CapturedCompanyDetails> fetch(String linkedinSlug) {
        log.debug("Company enrichment is off — {} stays as captured.", linkedinSlug);
        return Optional.empty();
    }
}
