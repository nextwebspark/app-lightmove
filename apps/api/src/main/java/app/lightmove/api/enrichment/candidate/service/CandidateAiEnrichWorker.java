package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.constant.AiEnrichTrigger;
import app.lightmove.api.candidate.model.CandidateAiEnrichRequested;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.InferredBackground;
import app.lightmove.api.candidate.model.NationalityReading;
import app.lightmove.api.candidate.service.CandidateScorecardService;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.config.EnrichmentSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.position.model.AssessmentBrief;
import app.lightmove.api.position.service.PositionService;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * A newly researched executive's automatic AI run, after the filing commits and off its thread: the background
 * proposals still missing, and a drafted assessment when the brief has competencies and nobody drafted one yet. Asks
 * the model nothing when there is nothing to do. Shaped like {@link CandidateEnrichmentWorker}, for its reasons.
 */
@Component
@Slf4j
class CandidateAiEnrichWorker {

    /** A People Search page files 25 people at once; this many run their model calls together, the rest wait. */
    private static final int CONCURRENT_RUNS = 4;

    private final CandidateAiEnricher enricher;
    private final CandidateNationalityClassifier nationalityClassifier;
    private final CandidateScorecardDrafter drafter;
    private final CandidateService candidates;
    private final CandidateScorecardService scorecards;
    private final PositionService positions;
    private final AiDraftWriter writer;
    private final LlmBudgetGuard llmBudget;
    private final EnrichmentSettings settings;
    private final Semaphore runs = new Semaphore(CONCURRENT_RUNS);

    CandidateAiEnrichWorker(CandidateAiEnricher enricher, CandidateNationalityClassifier nationalityClassifier,
                            CandidateScorecardDrafter drafter, CandidateService candidates,
                            CandidateScorecardService scorecards, PositionService positions, AiDraftWriter writer,
                            LlmBudgetGuard llmBudget, LightMoveProperties properties) {
        this.enricher = enricher;
        this.nationalityClassifier = nationalityClassifier;
        this.drafter = drafter;
        this.candidates = candidates;
        this.scorecards = scorecards;
        this.positions = positions;
        this.writer = writer;
        this.llmBudget = llmBudget;
        this.settings = properties.enrichment();
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void enrich(CandidateAiEnrichRequested request) {
        if (!settings.aiEnrichOnCapture()) {
            return;
        }
        runs.acquireUninterruptibly();
        try {
            candidates.dossierOf(request.projectId(), request.candidateId())
                    .ifPresent(dossier -> enrichFrom(dossier, request));
        } catch (ObjectOptimisticLockingFailureException raced) {
            log.info("Candidate {} was edited while its AI run wrote", request.candidateId());
        } catch (RuntimeException ex) {
            log.error("Failed AI run for candidate {}", request.candidateId(), ex);
        } finally {
            runs.release();
        }
    }

    private void enrichFrom(CandidateDossier dossier, CandidateAiEnrichRequested request) {
        UUID projectId = request.projectId();
        UUID candidateId = request.candidateId();
        BackgroundNeeds needs = BackgroundNeeds.of(dossier, candidates.hasNationalityReading(projectId, candidateId));
        AssessmentBrief brief = positions.assessmentBriefOf(request.workspaceId(), projectId);
        boolean drafting = !brief.competencies().competencies().isEmpty() && !scorecards.isAiDrafted(candidateId);
        if (!needs.any() && !drafting) {
            return;
        }
        if (request.trigger() == AiEnrichTrigger.CAPTURE) {
            llmBudget.require(LlmBudget.CANDIDATE_AI_ENRICH, request.requestedBy());
        }
        InferredBackground background = needs.inferring() ? enricher.infer(dossier).orElse(null) : null;
        NationalityReading reading = needs.classifying() ? nationalityClassifier.classify(dossier).orElse(null) : null;
        if (background != null || reading != null) {
            writer.writeBackground(projectId, candidateId, request.requestedBy(), background, reading);
        }
        if (drafting) {
            drafter.draft(dossier, brief).ifPresent(draft -> writer.writeScorecard(request.workspaceId(), projectId,
                    candidateId, request.requestedBy(), draft));
        }
    }
}
