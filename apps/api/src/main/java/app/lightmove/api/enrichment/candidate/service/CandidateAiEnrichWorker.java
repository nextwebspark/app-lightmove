package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.constant.AiEnrichTrigger;
import app.lightmove.api.candidate.model.CandidateAiEnrichRequested;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.config.EnrichmentSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.position.service.PositionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs a candidate's AI enrichment after the request commits and off its thread, so a capture's
 * research and the drawer's button both return before the model is asked. Shaped like
 * {@link CandidateEnrichmentWorker}, for its reasons.
 */
@Component
@Slf4j
class CandidateAiEnrichWorker {

    private final CandidateAiEnricher enricher;
    private final CandidateService candidates;
    private final PositionService positions;
    private final LlmBudgetGuard llmBudget;
    private final EnrichmentSettings settings;

    CandidateAiEnrichWorker(CandidateAiEnricher enricher, CandidateService candidates,
                            PositionService positions, LlmBudgetGuard llmBudget,
                            LightMoveProperties properties) {
        this.enricher = enricher;
        this.candidates = candidates;
        this.positions = positions;
        this.llmBudget = llmBudget;
        this.settings = properties.enrichment();
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void enrich(CandidateAiEnrichRequested request) {
        boolean fromCapture = request.trigger() == AiEnrichTrigger.CAPTURE;
        if (fromCapture && !settings.aiEnrichOnCapture()) {
            return;
        }
        try {
            if (fromCapture) {
                // The button spent this before answering 202; a capture spends it here.
                llmBudget.require(LlmBudget.CANDIDATE_AI_ENRICH, request.requestedBy());
            }
            candidates.dossierOf(request.projectId(), request.candidateId()).ifPresent(dossier ->
                    enricher.enrich(dossier, positions.briefOf(request.workspaceId(), request.projectId()))
                            .ifPresentOrElse(
                                    enrichment -> candidates.applyAiEnrichment(request.projectId(),
                                            request.candidateId(), enrichment),
                                    () -> recordFailure(request)));
        } catch (ObjectOptimisticLockingFailureException raced) {
            log.info("Candidate {} was edited while its AI enrichment ran", request.candidateId());
        } catch (RuntimeException ex) {
            log.error("Failed AI enrichment for candidate {}", request.candidateId(), ex);
            recordFailure(request);
        }
    }

    /** Best-effort: the drawer waiting on this run is told it failed rather than left to time out. */
    private void recordFailure(CandidateAiEnrichRequested request) {
        try {
            candidates.recordAiEnrichFailure(request.projectId(), request.candidateId());
        } catch (RuntimeException ex) {
            log.warn("Could not record the failed AI enrichment for candidate {}: {}",
                    request.candidateId(), ex.toString());
        }
    }
}
