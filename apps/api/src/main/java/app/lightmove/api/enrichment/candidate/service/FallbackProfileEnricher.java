package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.EnrichedProfile;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The dataset first, the live scrape only when it has to be: a primary answer whose career carries
 * not one position title is treated as a miss too — masked records and the dataset's skeleton rows
 * (companies and year ranges with every title null, seen live) both answer the lookup without
 * answering the question. When the fallback also comes back empty, a thin primary answer still
 * beats nothing.
 *
 * <p><b>A provider that throws has missed, not exploded.</b> An outage is precisely the case a
 * fallback exists for, so a 429 or a timeout from the dataset must reach the live scrape rather than
 * sail past it — and an exception from the scrape must not destroy the thin answer the dataset did
 * give us. Both delegates are therefore called defensively.
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
        log.info("Primary research {} for {} — falling back to the live scrape",
                answer.isPresent() ? "was thin" : "missed", linkedinUrl);
        Optional<EnrichedProfile> live = attempt(secondary, linkedinUrl, "live scrape");
        return live.isPresent() ? live : answer;
    }

    private static Optional<EnrichedProfile> attempt(LinkedInProfileEnricher enricher, String url,
                                                     String which) {
        try {
            return enricher.fetch(url);
        } catch (RuntimeException ex) {
            log.warn("The {} failed for {}: {}", which, url, ex.getMessage());
            return Optional.empty();
        }
    }

    private static boolean answersTheQuestion(EnrichedProfile answer) {
        return answer.career().stream().anyMatch(post -> post.title() != null);
    }
}
