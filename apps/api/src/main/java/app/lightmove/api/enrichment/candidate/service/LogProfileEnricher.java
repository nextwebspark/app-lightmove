package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.EnrichedProfile;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Answers every research request with nothing.
 *
 * <p>The default, for the reason {@code LogEmailSender} is: a fresh clone runs the whole capture flow
 * with no HarvestAPI account and no spend — the candidate simply keeps what the plugin read.
 */
@Slf4j
public class LogProfileEnricher implements LinkedInProfileEnricher {

    @Override
    public Optional<EnrichedProfile> fetch(String linkedinUrl) {
        log.debug("Enrichment is off — {} stays as captured.", linkedinUrl);
        return Optional.empty();
    }
}
