package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.CandidateCapturedEvent;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.service.CandidateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Researches a plugin-captured executive off the capture's thread. {@code AFTER_COMMIT} because it
 * updates the row the capture inserted, which a plain listener would race. Not {@code @Transactional}:
 * the vendor call must hold no connection, so the write crosses into
 * {@link CandidateService#applyResearch}. Failures are swallowed — a capture broken by its own
 * enrichment is a bug report.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class CandidateEnrichmentWorker {

    private final LinkedInProfileEnricher enricher;
    private final CandidateService candidates;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void enrich(CandidateCapturedEvent event) {
        try {
            enricher.fetch(event.linkedinUrl()).ifPresentOrElse(
                    profile -> {
                        candidates.applyResearch(event.projectId(), event.candidateId(), profile);
                        adoptPublicProfile(event, profile);
                    },
                    () -> log.info("No research found for candidate {}", event.candidateId()));
        } catch (ObjectOptimisticLockingFailureException raced) {
            // A re-capture inside the research window: @Version refusing the second write is the guard working.
            log.info("Candidate {} was enriched by a concurrent event", event.candidateId());
        } catch (RuntimeException ex) {
            log.error("Failed to enrich candidate {}", event.candidateId(), ex);
        }
    }

    /** After the research has committed, so losing the race for the vanity profile costs the address alone. */
    private void adoptPublicProfile(CandidateCapturedEvent event, EnrichedProfile profile) {
        try {
            candidates.adoptPublicProfile(event.projectId(), event.candidateId(), profile);
        } catch (DataIntegrityViolationException taken) {
            log.info("Candidate {} keeps its member-id address: its vanity profile was filed meanwhile",
                    event.candidateId());
        }
    }
}
