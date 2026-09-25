package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.CandidateResearchedEvent;
import app.lightmove.api.candidate.model.InferredBackground;
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
 * Infers a researched executive's missing background once the research itself has committed, so the
 * model call never delays what the vendor found. Shaped like {@link CandidateEnrichmentWorker}, for
 * its reasons.
 */
@Component
@Slf4j
class CandidateBackgroundWorker {

    private final CandidateBackgroundProposer proposer;
    private final CandidateService candidates;
    private final EnrichmentSettings settings;

    CandidateBackgroundWorker(CandidateBackgroundProposer proposer, CandidateService candidates,
                              LightMoveProperties properties) {
        this.proposer = proposer;
        this.candidates = candidates;
        this.settings = properties.enrichment();
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void infer(CandidateResearchedEvent event) {
        if (!settings.backgroundInference()) {
            return;
        }
        try {
            InferredBackground proposed = proposer.propose(event);
            if (!proposed.isEmpty()) {
                candidates.applyInferredBackground(event.projectId(), event.candidateId(), proposed);
            }
        } catch (ObjectOptimisticLockingFailureException raced) {
            log.info("Candidate {} was edited while its background was inferred", event.candidateId());
        } catch (RuntimeException ex) {
            log.error("Failed to infer background for candidate {}", event.candidateId(), ex);
        }
    }
}
