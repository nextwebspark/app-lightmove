package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.constant.AiEnrichTrigger;
import app.lightmove.api.candidate.constant.AiRunScope;
import app.lightmove.api.candidate.model.CandidateAiEnrichRequested;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.EnrichmentSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * A newly researched executive's background proposals, after the filing commits and off its thread: whichever of
 * gender, years and seniority is missing, and the nationality classifier while nationality is. Asks the model nothing
 * when nothing is missing. The assessment is never drafted here — only Assess with AI drafts it. Shaped like
 * {@link CandidateEnrichmentWorker}, for its reasons.
 */
@Component
@Slf4j
class CandidateAiEnrichWorker {

    /** A Find executives run files a batch at once; this many run their model calls together, the rest wait. */
    private static final int CONCURRENT_RUNS = 4;

    private final BackgroundProposer backgroundProposer;
    private final CandidateService candidates;
    private final CandidateMaterialReader materialReader;
    private final AiDraftWriter writer;
    private final LlmBudgetGuard llmBudget;
    private final AuditService audit;
    private final EnrichmentSettings settings;
    private final Semaphore runs = new Semaphore(CONCURRENT_RUNS);

    CandidateAiEnrichWorker(BackgroundProposer backgroundProposer, CandidateService candidates,
                            CandidateMaterialReader materialReader, AiDraftWriter writer, LlmBudgetGuard llmBudget,
                            AuditService audit, LightMoveProperties properties) {
        this.backgroundProposer = backgroundProposer;
        this.candidates = candidates;
        this.materialReader = materialReader;
        this.writer = writer;
        this.llmBudget = llmBudget;
        this.audit = audit;
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
        if (!needs.any()) {
            return;
        }
        if (request.trigger() == AiEnrichTrigger.CAPTURE) {
            llmBudget.require(LlmBudget.CANDIDATE_AI_ENRICH, request.requestedBy());
        }
        MaterialText material = materialReader.read(projectId, candidateId);
        audited(request, material);
        BackgroundProposals proposals = backgroundProposer.propose(dossier, material, needs);
        if (!proposals.holdsNothing()) {
            writer.writeBackground(projectId, candidateId, request.requestedBy(), proposals);
        }
    }

    /** Notes and documents leave for the model here as they do on a press, so the run is audited as a press is. */
    private void audited(CandidateAiEnrichRequested request, MaterialText material) {
        audit.event(ProjectEventType.CANDIDATE_AI_ENRICH_REQUESTED)
                .actor(request.requestedBy()).workspace(request.workspaceId())
                .target(AuditService.PROJECT_TARGET, request.projectId())
                .detail("candidateId", request.candidateId().toString())
                .detail("scope", AiRunScope.BACKGROUND.name())
                .detail("trigger", request.trigger().name())
                .detail("notesSent", String.valueOf(material.noteCount()))
                .detail("documentsSent", String.valueOf(material.documentCount()))
                .record();
    }
}
