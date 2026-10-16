package app.uncava.api.enrichment.candidate.service;

import app.uncava.api.billing.usage.constant.UsageKind;
import app.uncava.api.billing.usage.model.MeteredUse;
import app.uncava.api.billing.usage.service.FairUseGuard;
import app.uncava.api.billing.usage.service.UsageRecorder;
import app.uncava.api.candidate.constant.AiRunScope;
import app.uncava.api.candidate.dto.CandidateResponse;
import app.uncava.api.candidate.dto.CandidateScorecardResponse;
import app.uncava.api.candidate.model.AiScorecardDraft;
import app.uncava.api.candidate.model.CandidateDossier;
import app.uncava.api.candidate.service.CandidateScorecardService;
import app.uncava.api.candidate.service.CandidateService;
import app.uncava.api.core.audit.constant.ProjectEventType;
import app.uncava.api.core.audit.service.AuditService;
import app.uncava.api.core.error.constant.ErrorCode;
import app.uncava.api.core.error.model.ApiException;
import app.uncava.api.core.ratelimit.service.LlmBudget;
import app.uncava.api.core.ratelimit.service.LlmBudgetGuard;
import app.uncava.api.core.security.rbac.ProjectAudience;
import app.uncava.api.position.model.AssessmentBrief;
import app.uncava.api.position.service.PositionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The drawer's two AI buttons, answered while the consultant waits. Not transactional: no transaction is held across
 * a model call, and a use is recorded only once its answer is on file. The fair-use ceiling is therefore soft — two
 * presses at once both pass the check — and the per-user {@code LlmBudget} is what bounds the overshoot.
 */
@Service
@RequiredArgsConstructor
public class CandidateAiEnrichService {

    private final CandidateService candidates;
    private final CandidateScorecardService scorecards;
    private final PositionService positions;
    private final BackgroundProposer backgroundProposer;
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

        BackgroundProposals proposals = backgroundProposer.propose(dossier, material, needs);
        if (proposals.holdsNothing()) {
            throw ApiException.of(ErrorCode.AI_UNAVAILABLE);
        }
        CandidateResponse answer = writer.writeBackground(projectId, candidateId, userId, proposals);
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
        if (!scorecards.hasOpenFor(workspaceId, projectId, candidateId)) {
            throw ApiException.of(ErrorCode.CANDIDATE_ASSESSMENT_NOTHING_OPEN);
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
