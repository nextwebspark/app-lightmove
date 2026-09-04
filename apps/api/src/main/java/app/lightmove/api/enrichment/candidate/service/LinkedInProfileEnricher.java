package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.EnrichedProfile;
import java.util.Optional;

/**
 * Researches a live profile URL into the details a researcher would otherwise type by hand.
 *
 * <p>A port for the reason {@code EmailSender} is one: the flow is exercised end to end in tests with
 * no vendor, no network and no spend, and swapping the provider is one adapter. Empty means the
 * profile could not be researched — not found, private, or the provider is off — and the candidate
 * simply keeps what the plugin read.
 */
public interface LinkedInProfileEnricher {

    Optional<EnrichedProfile> fetch(String linkedinUrl);
}
