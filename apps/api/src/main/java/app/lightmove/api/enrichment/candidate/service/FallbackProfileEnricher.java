package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.EnrichedProfile;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The dataset first, the live scrape when it misses — and an answer with no position title counts as
 * a miss (masked and skeleton rows). A thin primary answer still beats an empty fallback. A delegate
 * that throws has missed: an outage is what the fallback is for. Both throwing is an outage, not a miss,
 * and is thrown, so the Add form's lookup says "try again" rather than "no such profile".
 */
@RequiredArgsConstructor
@Slf4j
public class FallbackProfileEnricher implements LinkedInProfileEnricher {

    private final LinkedInProfileEnricher primary;
    private final LinkedInProfileEnricher secondary;

    @Override
    public Optional<EnrichedProfile> fetch(String linkedinUrl) {
        ResearchAttempt dataset = attempt(primary, linkedinUrl, "dataset");
        if (dataset.answer().isPresent() && answersTheQuestion(dataset.answer().get())) {
            return dataset.answer();
        }
        // Debug: a LinkedIn URL identifies the person, which VendorException keeps out of its message.
        log.debug("Primary research {} for {} — falling back to the live scrape",
                dataset.answer().isPresent() ? "was thin" : "missed", linkedinUrl);
        ResearchAttempt live = attempt(secondary, linkedinUrl, "live scrape");
        if (live.answer().isPresent()) {
            return live.answer();
        }
        if (dataset.failure() != null && live.failure() != null) {
            live.failure().addSuppressed(dataset.failure());
            throw live.failure();
        }
        return dataset.answer();
    }

    private static ResearchAttempt attempt(LinkedInProfileEnricher enricher, String url, String which) {
        try {
            return new ResearchAttempt(enricher.fetch(url), null);
        } catch (RuntimeException ex) {
            log.warn("The {} failed: {}", which, ex.getMessage());
            log.debug("The {} failed for {}", which, url);
            return new ResearchAttempt(Optional.empty(), ex);
        }
    }

    private record ResearchAttempt(Optional<EnrichedProfile> answer, RuntimeException failure) {}

    private static boolean answersTheQuestion(EnrichedProfile answer) {
        return answer.career().stream().anyMatch(post -> post.title() != null);
    }
}
