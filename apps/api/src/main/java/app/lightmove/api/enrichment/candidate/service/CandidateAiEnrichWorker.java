package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.constant.AiEnrichTrigger;
import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.candidate.model.CandidateAiEnrichRequested;
import app.lightmove.api.candidate.model.CandidateAiEnrichment;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.NationalityReading;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.config.EnrichmentSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.position.service.PositionService;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs a candidate's AI enrichment after the request commits, off its thread: the assessment call,
 * and the nationality classifier while nationality is still empty. One budget unit covers both — one
 * press is one enrichment. Shaped like {@link CandidateEnrichmentWorker}, for its reasons.
 */
@Component
@Slf4j
class CandidateAiEnrichWorker {

    private final CandidateAiEnricher enricher;
    private final CandidateNationalityClassifier nationalityClassifier;
    private final CandidateService candidates;
    private final PositionService positions;
    private final LlmBudgetGuard llmBudget;
    private final EnrichmentSettings settings;

    CandidateAiEnrichWorker(CandidateAiEnricher enricher, CandidateNationalityClassifier nationalityClassifier,
                            CandidateService candidates,
                            PositionService positions, LlmBudgetGuard llmBudget,
                            LightMoveProperties properties) {
        this.enricher = enricher;
        this.nationalityClassifier = nationalityClassifier;
        this.candidates = candidates;
        this.positions = positions;
        this.llmBudget = llmBudget;
        this.settings = properties.enrichment();
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void enrich(CandidateAiEnrichRequested request) {
        boolean automatic = request.trigger() != AiEnrichTrigger.BUTTON;
        if (automatic && !settings.aiEnrichOnCapture()) {
            return;
        }
        try {
            if (request.trigger() == AiEnrichTrigger.CAPTURE) {
                // The button spent this before answering 202; a capture spends it here.
                llmBudget.require(LlmBudget.CANDIDATE_AI_ENRICH, request.requestedBy());
            }
            candidates.dossierOf(request.projectId(), request.candidateId())
                    .ifPresent(dossier -> enrichFrom(dossier, request));
        } catch (ObjectOptimisticLockingFailureException raced) {
            log.info("Candidate {} was edited while its AI enrichment ran", request.candidateId());
        } catch (RuntimeException ex) {
            log.error("Failed AI enrichment for candidate {}", request.candidateId(), ex);
            recordFailure(request);
        }
    }

    private void enrichFrom(CandidateDossier dossier, CandidateAiEnrichRequested request) {
        Optional<CandidateAiEnrichment> assessed =
                enricher.enrich(dossier, positions.briefOf(request.workspaceId(), request.projectId()));
        NationalityReading reading = dossier.missingBackground().contains(BackgroundField.NATIONALITY)
                ? nationalityClassifier.classify(dossier).orElse(null)
                : null;
        if (assessed.isEmpty() && reading == null) {
            recordFailure(request);
            return;
        }
        CandidateAiEnrichment enrichment = assessed
                .map(found -> new CandidateAiEnrichment(found.background(), found.assessment(), reading))
                .orElseGet(() -> new CandidateAiEnrichment(null, null, reading));
        candidates.applyAiEnrichment(request.projectId(), request.candidateId(), enrichment);
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
