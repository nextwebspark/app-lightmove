package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.EnrichedProfile;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The dataset first, the live scrape when it misses — and an answer with no position title counts as
 * a miss (masked and skeleton rows). A thin primary answer still beats an empty fallback. A delegate
 * that throws has missed: an outage is what the fallback is for.
 */
@RequiredArgsConstructor
@Slf4j
public class FallbackProfileEnricher implements LinkedInProfileEnricher {

    private final LinkedInProfileEnricher primary;
    private final LinkedInProfileEnricher secondary;

    @Override
    public Optional<EnrichedProfile> fetch(String linkedinUrl) {
        Optional<EnrichedProfile> answer = attempt(primary, linkedinUrl, "dataset");
        if (answer.isPresent() && answersTheQuestion(answer.get())) {
            return answer;
        }
        // Debug: a LinkedIn URL identifies the person, which VendorException keeps out of its message.
        log.debug("Primary research {} for {} — falling back to the live scrape",
                answer.isPresent() ? "was thin" : "missed", linkedinUrl);
        Optional<EnrichedProfile> live = attempt(secondary, linkedinUrl, "live scrape");
        return live.isPresent() ? live : answer;
    }

    private static Optional<EnrichedProfile> attempt(LinkedInProfileEnricher enricher, String url,
                                                     String which) {
        try {
            return enricher.fetch(url);
        } catch (RuntimeException ex) {
            log.warn("The {} failed: {}", which, ex.getMessage());
            log.debug("The {} failed for {}", which, url);
            return Optional.empty();
        }
    }

    private static boolean answersTheQuestion(EnrichedProfile answer) {
        return answer.career().stream().anyMatch(post -> post.title() != null);
    }
}
