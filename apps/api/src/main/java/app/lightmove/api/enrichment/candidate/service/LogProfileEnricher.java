package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.EnrichedProfile;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/** The default: answers nothing, so a fresh clone runs the capture flow with no vendor or spend. */
@Slf4j
public class LogProfileEnricher implements LinkedInProfileEnricher {

    @Override
    public Optional<EnrichedProfile> fetch(String linkedinUrl) {
        log.debug("Enrichment is off — {} stays as captured.", linkedinUrl);
        return Optional.empty();
    }
}
