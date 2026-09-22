package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.CandidateCapturedEvent;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.config.EnrichmentSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Researches a plugin-captured executive, off the capture's thread and after its commit.
 *
 * <p>{@code AFTER_COMMIT} because this worker <i>updates</i> the row the capture inserted — a plain
 * listener (or a direct {@code @Async} call from the service) races the commit and reads a row its
 * own transaction cannot see yet. {@code @Async} because a live scrape takes seconds and the capture
 * already returned 201.
 *
 * <p>Deliberately not {@code @Transactional}: the vendor call must not hold a database connection —
 * a retry's backoff would hold it for seconds — so the write crosses back into
 * {@link CandidateService#applyResearch}, which opens its own transaction. Failures are swallowed and
 * logged: enrichment lost is a re-capture; a capture broken by its own enrichment is a bug report.
 */
@Component
@Slf4j
class CandidateEnrichmentWorker {

    private final LinkedInProfileEnricher enricher;
    private final CandidateBackgroundProposer background;
    private final CandidateService candidates;
    private final EnrichmentSettings settings;

    CandidateEnrichmentWorker(LinkedInProfileEnricher enricher, CandidateBackgroundProposer background,
                             CandidateService candidates, LightMoveProperties properties) {
        this.enricher = enricher;
        this.background = background;
        this.candidates = candidates;
        this.settings = properties.enrichment();
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void enrich(CandidateCapturedEvent event) {
        try {
            enricher.fetch(event.linkedinUrl()).ifPresentOrElse(
                    profile -> candidates.applyResearch(event.projectId(), event.candidateId(),
                            inferBackground(event, profile)),
                    () -> log.info("No research found for candidate {}", event.candidateId()));
        } catch (ObjectOptimisticLockingFailureException raced) {
            // Two events for one candidate — a re-capture inside the research window. @Version refuses
            // the second write, which is the guard working, not a failure worth a stack trace.
            log.info("Candidate {} was enriched by a concurrent event", event.candidateId());
        } catch (RuntimeException ex) {
            log.error("Failed to enrich candidate {}", event.candidateId(), ex);
        }
    }

    /** Nationality, gender and years of experience, read off the same research (issue #458). */
    private EnrichedProfile inferBackground(CandidateCapturedEvent event, EnrichedProfile profile) {
        return settings.backgroundInference()
                ? background.propose(event.addedBy(), event.fullName(), profile)
                : profile;
    }
}
