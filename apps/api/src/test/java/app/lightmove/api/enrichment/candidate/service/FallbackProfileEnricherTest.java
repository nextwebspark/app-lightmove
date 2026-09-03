package app.lightmove.api.enrichment.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.EnrichedProfile;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The dataset answers when it can; the live scrape is spent only on a miss or a thin record. */
class FallbackProfileEnricherTest {

    private static final EnrichedProfile RICH = profile(
            List.of(new CandidateCareerEntry("RetailCo", "CEO", "2025 – Present")));
    // The dataset's skeleton shape, seen live: a company and a year range, every title null.
    private static final EnrichedProfile THIN = profile(
            List.of(new CandidateCareerEntry("RetailCo", null, "2017 – 2018")));
    private static final EnrichedProfile LIVE = profile(
            List.of(new CandidateCareerEntry("LiveCo", "CFO", "2020 – Present")));

    @Test
    @DisplayName("a full dataset answer never spends a live scrape")
    void aFullAnswerNeverFallsBack() {
        AtomicInteger liveCalls = new AtomicInteger();
        FallbackProfileEnricher enricher = new FallbackProfileEnricher(
                url -> Optional.of(RICH),
                url -> { liveCalls.incrementAndGet(); return Optional.of(LIVE); });

        assertThat(enricher.fetch("https://www.linkedin.com/in/x/")).contains(RICH);
        assertThat(liveCalls.get()).isZero();
    }

    @Test
    @DisplayName("a dataset miss falls through to the live scrape")
    void aMissFallsThrough() {
        FallbackProfileEnricher enricher =
                new FallbackProfileEnricher(url -> Optional.empty(), url -> Optional.of(LIVE));

        assertThat(enricher.fetch("https://www.linkedin.com/in/x/")).contains(LIVE);
    }

    @Test
    @DisplayName("a skeleton dataset record — a career with no titles — is a miss too")
    void aThinRecordIsAMiss() {
        FallbackProfileEnricher enricher =
                new FallbackProfileEnricher(url -> Optional.of(THIN), url -> Optional.of(LIVE));

        assertThat(enricher.fetch("https://www.linkedin.com/in/x/")).contains(LIVE);
    }

    @Test
    @DisplayName("a dataset outage falls through rather than sailing past the fallback")
    void anOutageFallsThrough() {
        FallbackProfileEnricher enricher = new FallbackProfileEnricher(
                url -> { throw new IllegalStateException("429 Too Many Requests"); },
                url -> Optional.of(LIVE));

        // The whole point of arming a fallback is the provider having a bad minute.
        assertThat(enricher.fetch("https://www.linkedin.com/in/x/")).contains(LIVE);
    }

    @Test
    @DisplayName("a failing live scrape does not destroy the thin answer the dataset gave")
    void aFailingScrapeKeepsTheThinAnswer() {
        FallbackProfileEnricher enricher = new FallbackProfileEnricher(
                url -> Optional.of(THIN),
                url -> { throw new IllegalStateException("503 Service Unavailable") ; });

        assertThat(enricher.fetch("https://www.linkedin.com/in/x/")).contains(THIN);
    }

    @Test
    @DisplayName("when the live scrape also misses, a thin answer still beats nothing")
    void aThinAnswerBeatsNothing() {
        FallbackProfileEnricher enricher =
                new FallbackProfileEnricher(url -> Optional.of(THIN), url -> Optional.empty());

        assertThat(enricher.fetch("https://www.linkedin.com/in/x/")).contains(THIN);
    }

    private static EnrichedProfile profile(List<CandidateCareerEntry> career) {
        return new EnrichedProfile("Title", null, null, null, null, null, null, career,
                null, null, null, null);
    }
}
