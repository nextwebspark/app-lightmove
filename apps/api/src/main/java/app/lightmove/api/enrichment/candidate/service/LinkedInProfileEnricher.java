package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.EnrichedProfile;
import java.util.Optional;

/**
 * Researches a live profile URL. A port so the flow tests end to end with no vendor; empty means not
 * researched (not found, private, or provider off) and the candidate keeps what the plugin read.
 */
public interface LinkedInProfileEnricher {

    Optional<EnrichedProfile> fetch(String linkedinUrl);
}
