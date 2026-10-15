package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.AiRunScope;
import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.constant.ScorecardStatus;
import app.lightmove.api.candidate.dto.CandidateScorecardResponse;
import app.lightmove.api.candidate.dto.CriterionScoreRequest;
import app.lightmove.api.candidate.dto.SaveScorecardRequest;
import app.lightmove.api.candidate.model.AiScorecardDraft;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateScorecard;
import app.lightmove.api.candidate.model.CriterionScore;
import app.lightmove.api.candidate.model.ScorecardCriterion;
import app.lightmove.api.candidate.model.ScorecardTally;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.CandidateScorecardRepository;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.stream.ProjectStreamKind;
import app.lightmove.api.core.stream.ProjectStreamPublisher;
import app.lightmove.api.position.model.BriefCompetencies;
import app.lightmove.api.position.service.PositionService;
import app.lightmove.api.project.repository.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A consultant's assessment of an executive against the position's brief — the drawer's Assessment tab. Read
 * against the brief as it stands, since the competencies can change after a score was given: a renamed or
 * removed one keeps its score as retired, a new one arrives unscored. Staff-only, behind WORK_EXECUTE.
 */
@Service
@RequiredArgsConstructor
public class CandidateScorecardService {

    private final ProjectRepository projects;
    private final CandidateRepository candidates;
    private final CandidateScorecardRepository scorecards;
    private final PositionService positions;
    private final UserRepository users;
    private final PersonActivityRecorder activity;
    private final AuditService audit;
    private final ProjectStreamPublisher stream;

    @Transactional(readOnly = true)
    public CandidateScorecardResponse read(UUID workspaceId, UUID projectId, UUID candidateId) {
        projects.requireInWorkspace(projectId, workspaceId);
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        CandidateScorecard scorecard = scorecards.findByCandidateIdAndWorkspaceId(candidate.getId(), workspaceId)
                .orElse(null);
        return respond(scorecard, briefOf(workspaceId, projectId));
    }

    @Transactional
    public CandidateScorecardResponse save(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                           SaveScorecardRequest request, HttpServletRequest httpRequest) {
        projects.requireInWorkspace(projectId, workspaceId);
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        CandidateScorecard scorecard = scorecards.findByCandidateIdAndWorkspaceId(candidate.getId(), workspaceId)
                .orElse(null);
        Long heldVersion = scorecard == null ? null : scorecard.getVersion();
        if (!Objects.equals(heldVersion, request.version())) {
            throw ApiException.of(ErrorCode.CANDIDATE_ASSESSMENT_STALE);
        }
        BriefScoring brief = briefOf(workspaceId, projectId);
        boolean wasReviewed = scorecard != null && scorecard.getStatus() == ScorecardStatus.REVIEWED;
        if (scorecard == null) {
            scorecard = CandidateScorecard.startFor(candidate, workspaceId);
        }
        List<CriterionScore> held = scorecard.getScores();
        scorecard.record(merged(held, request.scores(), brief.criteria()), request.report().toReport(),
                request.markReviewed(), userId, Instant.now());
        CandidateScorecard saved = saveOrRefuseStale(scorecard);

        if (request.markReviewed()) {
            activity.record(candidate, userId, PersonActivityKind.ASSESSMENT_REVIEWED);
        }
        ScorecardTally tally = ScorecardScoring.tally(brief.criteria(), brief.technicalShare(), saved.getScores());
        audit.projectEvent(request.markReviewed() ? ProjectEventType.CANDIDATE_ASSESSMENT_REVIEWED
                        : ProjectEventType.CANDIDATE_ASSESSMENT_SAVED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidate.getId().toString())
                .detail("scoredCount", String.valueOf(tally.scoredCount()))
                .detail("wasReviewed", String.valueOf(wasReviewed))
                .record();
        return respond(saved, brief);
    }

    /**
     * The AI's draft written over everything no consultant holds a judgement on, against the brief as it stands now:
     * a criterion the brief dropped while the model read is let go, and each score takes today's weight.
     * {@code REQUIRES_NEW} because the enrichment worker calls this from an {@code AFTER_COMMIT} callback, where a
     * joined transaction writes nothing; a racing save fails it, and the caller asks again over the fresh row.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CandidateScorecardResponse applyAiDraft(UUID workspaceId, UUID projectId, UUID candidateId,
                                                   UUID requestedBy, AiScorecardDraft draft) {
        projects.requireInWorkspace(projectId, workspaceId);
        Candidate candidate = candidates.requireInProject(candidateId, projectId);
        BriefScoring brief = briefOf(workspaceId, projectId);
        CandidateScorecard scorecard = scorecards.findByCandidateIdAndWorkspaceId(candidate.getId(), workspaceId)
                .orElseGet(() -> CandidateScorecard.startFor(candidate, workspaceId));
        scorecard.applyAiDraft(alignedToBrief(draft.scores(), brief.criteria()), draft.report(), Instant.now());
        CandidateScorecard saved = scorecards.saveAndFlush(scorecard);
        activity.record(candidate, requestedBy, PersonActivityKind.AI_ASSESSED,
                PersonActivityDetails.of("scope", AiRunScope.ASSESSMENT));
        stream.publish(projectId, ProjectStreamKind.CANDIDATE_ENRICHED);
        return respond(saved, brief);
    }

    @Transactional(readOnly = true)
    public boolean isAiDrafted(UUID candidateId) {
        return scorecards.existsByCandidateIdAndAiDraftedAtIsNotNull(candidateId);
    }

    static List<CriterionScore> alignedToBrief(List<CriterionScore> drafted, List<ScorecardCriterion> brief) {
        Map<String, ScorecardCriterion> briefByKey = new LinkedHashMap<>();
        brief.forEach(criterion -> briefByKey.putIfAbsent(criterion.matchKey(), criterion));
        Map<String, CriterionScore> aligned = new LinkedHashMap<>();
        for (CriterionScore score : drafted) {
            ScorecardCriterion criterion = briefByKey.get(score.matchKey());
            if (criterion != null) {
                aligned.putIfAbsent(criterion.matchKey(), new CriterionScore(criterion.panel(), criterion.name(),
                        criterion.weight(), score.score(), score.evidence()));
            }
        }
        return new ArrayList<>(aligned.values());
    }

    /**
     * A teammate's save landing between this one's read and its write is the same refusal as a stale version, never
     * a generic conflict: the first-save race is the unique constraint's (mapped where every constraint is), an update
     * race {@code @Version}'s.
     */
    private CandidateScorecard saveOrRefuseStale(CandidateScorecard scorecard) {
        try {
            return scorecards.saveAndFlush(scorecard);
        } catch (ObjectOptimisticLockingFailureException raced) {
            throw ApiException.of(ErrorCode.CANDIDATE_ASSESSMENT_STALE);
        }
    }

    /**
     * What a save leaves on file: every score sent against a criterion the brief has today, under the brief's own
     * spelling and weight, and every score already held against a criterion it no longer has — the editor never
     * sends those, and dropping them would lose a consultant's judgement to a teammate's rename. A score sent for a
     * criterion the brief dropped while the tab was open is let go.
     */
    static List<CriterionScore> merged(List<CriterionScore> held, List<CriterionScoreRequest> sent,
                                       List<ScorecardCriterion> brief) {
        Map<String, ScorecardCriterion> briefByKey = new LinkedHashMap<>();
        brief.forEach(criterion -> briefByKey.putIfAbsent(criterion.matchKey(), criterion));
        Map<String, CriterionScore> kept = new LinkedHashMap<>();
        for (CriterionScoreRequest score : sent) {
            CompetencyPanel panel = ApiValueEnum.require(CompetencyPanel.class, score.panel(), "competency panel");
            ScorecardCriterion criterion = briefByKey.get(CriterionScore.keyOf(panel, score.name()));
            if (criterion == null) {
                continue;
            }
            String evidence = score.evidence() == null || score.evidence().isBlank() ? null : score.evidence();
            CriterionScore stored = new CriterionScore(criterion.panel(), criterion.name(), criterion.weight(),
                    score.score(), evidence);
            if (!stored.holdsNothing()) {
                kept.putIfAbsent(stored.matchKey(), stored);
            }
        }
        for (CriterionScore score : held) {
            if (!briefByKey.containsKey(score.matchKey())) {
                kept.putIfAbsent(score.matchKey(), score);
            }
        }
        return new ArrayList<>(kept.values());
    }

    private BriefScoring briefOf(UUID workspaceId, UUID projectId) {
        BriefCompetencies brief = positions.competenciesOf(workspaceId, projectId);
        List<ScorecardCriterion> criteria = brief.competencies().stream()
                .map(competency -> new ScorecardCriterion(competency.panel(), competency.name(),
                        competency.description(), competency.weight()))
                .toList();
        return new BriefScoring(criteria, brief.technicalShare());
    }

    private CandidateScorecardResponse respond(CandidateScorecard scorecard, BriefScoring brief) {
        List<CriterionScore> held = scorecard == null ? List.of() : scorecard.getScores();
        ScorecardTally tally = ScorecardScoring.tally(brief.criteria(), brief.technicalShare(), held);
        return CandidateScorecardResponse.of(scorecard, tally,
                scorecard == null ? null : nameOf(scorecard.getUpdatedBy()),
                scorecard == null ? null : nameOf(scorecard.getReviewedBy()));
    }

    private String nameOf(UUID userId) {
        return userId == null ? null : users.findById(userId).map(User::getFullName).orElse(null);
    }

    private record BriefScoring(List<ScorecardCriterion> criteria, int technicalShare) {}
}
