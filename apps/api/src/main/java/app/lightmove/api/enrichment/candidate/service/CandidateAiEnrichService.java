package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.model.MeteredUse;
import app.lightmove.api.billing.usage.service.FairUseGuard;
import app.lightmove.api.billing.usage.service.UsageRecorder;
import app.lightmove.api.candidate.constant.AiRunScope;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.CandidateScorecardResponse;
import app.lightmove.api.candidate.model.AiScorecardDraft;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.InferredBackground;
import app.lightmove.api.candidate.model.NationalityReading;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.core.security.rbac.ProjectAudience;
import app.lightmove.api.position.model.AssessmentBrief;
import app.lightmove.api.position.service.PositionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The drawer's two AI buttons, answered while the consultant waits. Not transactional: no transaction is held across
 * a model call, and a use is recorded only once its answer is on file.
 */
@Service
@RequiredArgsConstructor
public class CandidateAiEnrichService {

    private final CandidateService candidates;
    private final PositionService positions;
    private final CandidateAiEnricher enricher;
    private final CandidateNationalityClassifier nationalityClassifier;
    private final CandidateScorecardDrafter drafter;
    private final CandidateMaterialReader materialReader;
    private final AiDraftWriter writer;
    private final LlmBudgetGuard llmBudget;
    private final FairUseGuard fairUse;
    private final UsageRecorder usage;
    private final AuditService audit;

    /** A background with nothing left to propose answers as it stands, spending nothing. */
    public CandidateResponse suggestBackground(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                               HttpServletRequest httpRequest) {
        CandidateDossier dossier = dossierOf(workspaceId, projectId, candidateId);
        BackgroundNeeds needs = BackgroundNeeds.of(dossier, candidates.hasNationalityReading(projectId, candidateId));
        if (!needs.any()) {
            return candidates.get(workspaceId, projectId, candidateId, ProjectAudience.STAFF);
        }
        llmBudget.require(LlmBudget.CANDIDATE_AI_ENRICH, userId);
        fairUse.check(workspaceId, userId, UsageKind.AI_ENRICH, 1);
        MaterialText material = materialReader.read(projectId, candidateId);
        requested(AiRunScope.BACKGROUND, material, userId, workspaceId, projectId, candidateId, httpRequest);

        InferredBackground background = needs.inferring() ? enricher.infer(dossier, material).orElse(null) : null;
        NationalityReading reading = needs.classifying()
                ? nationalityClassifier.classify(dossier, material).orElse(null) : null;
        if (background == null && reading == null) {
            throw ApiException.of(ErrorCode.AI_UNAVAILABLE);
        }
        CandidateResponse answer = writer.writeBackground(projectId, candidateId, userId, background, reading);
        usage.record(MeteredUse.of(workspaceId, userId, projectId, UsageKind.AI_ENRICH, 1));
        return answer;
    }

    public CandidateScorecardResponse assess(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                             HttpServletRequest httpRequest) {
        CandidateDossier dossier = dossierOf(workspaceId, projectId, candidateId);
        AssessmentBrief brief = positions.assessmentBriefOf(workspaceId, projectId);
        if (brief.competencies().competencies().isEmpty()) {
            throw ApiException.of(ErrorCode.CANDIDATE_ASSESSMENT_NO_CRITERIA);
        }
        llmBudget.require(LlmBudget.CANDIDATE_AI_ASSESSMENT, userId);
        fairUse.check(workspaceId, userId, UsageKind.AI_ENRICH, 1);
        MaterialText material = materialReader.read(projectId, candidateId);
        requested(AiRunScope.ASSESSMENT, material, userId, workspaceId, projectId, candidateId, httpRequest);

        AiScorecardDraft draft = drafter.draft(dossier, brief, material)
                .orElseThrow(() -> ApiException.of(ErrorCode.AI_UNAVAILABLE));
        CandidateScorecardResponse answer = writer.writeScorecard(workspaceId, projectId, candidateId, userId, draft);
        usage.record(MeteredUse.of(workspaceId, userId, projectId, UsageKind.AI_ENRICH, 1));
        return answer;
    }

    private CandidateDossier dossierOf(UUID workspaceId, UUID projectId, UUID candidateId) {
        candidates.requireCandidate(workspaceId, projectId, candidateId);
        return candidates.dossierOf(projectId, candidateId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /** Says how many notes and documents left for the model with the profile: a CV sent out is a CV out of the app. */
    private void requested(AiRunScope scope, MaterialText material, UUID userId, UUID workspaceId, UUID projectId,
                           UUID candidateId, HttpServletRequest httpRequest) {
        audit.projectEvent(ProjectEventType.CANDIDATE_AI_ENRICH_REQUESTED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("scope", scope.name())
                .detail("notesSent", String.valueOf(material.noteCount()))
                .detail("documentsSent", String.valueOf(material.documentCount()))
                .record();
    }
}
