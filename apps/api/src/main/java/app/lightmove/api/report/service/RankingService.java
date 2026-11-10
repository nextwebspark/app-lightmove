package app.lightmove.api.report.service;

import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.model.CriterionScore;
import app.lightmove.api.candidate.model.ProjectScorecards;
import app.lightmove.api.candidate.model.ScorecardCriterion;
import app.lightmove.api.candidate.model.ScorecardStanding;
import app.lightmove.api.candidate.model.ScoringBrief;
import app.lightmove.api.candidate.service.CandidateScorecardService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ReportSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.report.dto.RankedAssessmentDto;
import app.lightmove.api.report.dto.RankedCandidateDto;
import app.lightmove.api.report.dto.RankedScoreDto;
import app.lightmove.api.report.dto.RankingCriterionDto;
import app.lightmove.api.report.dto.RankingResponse;
import app.lightmove.api.report.dto.RankingScenarioDto;
import app.lightmove.api.report.dto.SaveRankingScenarioRequest;
import app.lightmove.api.report.model.RankingScenario;
import app.lightmove.api.report.repository.RankingScenarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Ranking chapter: every assessed executive of a position against its brief, and the one scenario the team
 * reweighs them under. The order and every sentence about it are the screen's, computed from these numbers as a
 * slider moves; the server keeps the scenario and never writes the brief.
 */
@Service
public class RankingService {

    private final CandidateScorecardService scorecards;
    private final RankingScenarioRepository scenarios;
    private final UserRepository users;
    private final AuditService audit;
    private final ReportSettings caps;

    public RankingService(CandidateScorecardService scorecards, RankingScenarioRepository scenarios,
                          UserRepository users, AuditService audit, LightMoveProperties properties) {
        this.scorecards = scorecards;
        this.scenarios = scenarios;
        this.users = users;
        this.audit = audit;
        this.caps = properties.report();
    }

    @Transactional(readOnly = true)
    public RankingResponse read(UUID workspaceId, UUID projectId) {
        ProjectScorecards assessed = scorecards.rankingInputsOf(workspaceId, projectId, caps.maxRanked());
        List<ScorecardCriterion> brief = assessed.criteria();
        RankingScenarioDto scenario = scenarios.findByProjectIdAndWorkspaceId(projectId, workspaceId)
                .map(saved -> toDto(saved, brief, assessed.technicalShare()))
                .orElse(null);
        return new RankingResponse(
                brief.stream().map(RankingService::toDto).toList(),
                assessed.technicalShare(),
                assessed.standings().stream().map(RankingService::toDto).toList(),
                Math.max(0, assessed.mappedCount() - assessed.assessedCount()),
                assessed.mappedCount(),
                assessed.truncated(),
                scenario);
    }

    /**
     * Keeps the scenario the screen holds, or clears it when it is the brief's own weighting. A teammate's save in
     * between is refused rather than overwritten, and so is a scenario naming competencies the brief no longer has.
     */
    @Transactional
    public RankingScenarioDto save(UUID userId, UUID workspaceId, UUID projectId, SaveRankingScenarioRequest request,
                                   HttpServletRequest httpRequest) {
        ScoringBrief brief = scorecards.scoringBriefOf(workspaceId, projectId);
        RankingScenario held = scenarios.findByProjectIdAndWorkspaceId(projectId, workspaceId).orElse(null);
        Long heldVersion = held == null ? null : held.getVersion();
        if (!Objects.equals(heldVersion, request.version())) {
            throw ApiException.of(ErrorCode.RANKING_SCENARIO_STALE);
        }
        RankingScenarioRules.refuseUnfit(request.weights(), brief.criteria());

        if (RankingScenarioRules.isBaseline(request.technicalShare(), request.weights(), brief.criteria(),
                brief.technicalShare())) {
            if (held != null) {
                deleteOrRefuseStale(held);
                audit.projectEvent(ProjectEventType.RANKING_SCENARIO_CLEARED, userId, workspaceId, projectId,
                        httpRequest).record();
            }
            return null;
        }

        RankingScenario scenario = held == null ? RankingScenario.startFor(workspaceId, projectId) : held;
        Map<String, Integer> weights = RankingScenarioRules.weightsOnto(request.weights(), brief.criteria());
        scenario.record(request.technicalShare(), weights, request.rationaleOrNull(),
                RankingScenarioRules.weightingOf(brief.criteria(), brief.technicalShare()), userId);
        RankingScenario saved = saveOrRefuseStale(scenario);
        audit.projectEvent(ProjectEventType.RANKING_SCENARIO_SAVED, userId, workspaceId, projectId, httpRequest)
                .detail("technicalShare", String.valueOf(saved.getTechnicalShare()))
                .detail("changedCount", String.valueOf(RankingScenarioRules.changedCount(weights, brief.criteria())))
                .record();
        return toDto(saved, brief.criteria(), brief.technicalShare());
    }

    private RankingScenario saveOrRefuseStale(RankingScenario scenario) {
        try {
            return scenarios.saveAndFlush(scenario);
        } catch (ObjectOptimisticLockingFailureException raced) {
            throw ApiException.of(ErrorCode.RANKING_SCENARIO_STALE);
        }
    }

    private void deleteOrRefuseStale(RankingScenario scenario) {
        try {
            scenarios.delete(scenario);
            scenarios.flush();
        } catch (ObjectOptimisticLockingFailureException raced) {
            throw ApiException.of(ErrorCode.RANKING_SCENARIO_STALE);
        }
    }

    private RankingScenarioDto toDto(RankingScenario scenario, List<ScorecardCriterion> brief, int briefShare) {
        return new RankingScenarioDto(
                scenario.getVersion(),
                scenario.getTechnicalShare(),
                RankingScenarioRules.weightsOnto(scenario.getWeights(), brief),
                scenario.getRationale(),
                scenario.getUpdatedAt(),
                scenario.getUpdatedBy() == null ? null
                        : users.findById(scenario.getUpdatedBy()).map(User::getFullName).orElse(null),
                RankingScenarioRules.briefChangedSince(scenario.getSavedAgainst(), brief, briefShare),
                RankingScenarioRules.droppedSince(scenario.getSavedAgainst(), brief));
    }

    private static RankingCriterionDto toDto(ScorecardCriterion criterion) {
        return new RankingCriterionDto(criterion.matchKey(), criterion.panel().value(), criterion.name(),
                criterion.description(), criterion.weight());
    }

    private static RankedCandidateDto toDto(ScorecardStanding standing) {
        CandidateResponse candidate = standing.candidate();
        List<RankedScoreDto> scores = standing.tally().panels().stream()
                .flatMap(panel -> panel.criteria().stream())
                .map(scored -> new RankedScoreDto(CriterionScore.keyOf(scored.panel(), scored.name()),
                        scored.score(), scored.evidence() != null && !scored.evidence().isBlank()))
                .toList();
        RankedAssessmentDto assessment = new RankedAssessmentDto(
                standing.status().value(),
                standing.updatedAt(),
                standing.reviewedAt(),
                standing.reviewedByName(),
                standing.tally().overall(),
                standing.tally().scoredCount(),
                standing.tally().criteriaCount(),
                standing.tally().retired().size(),
                scores);
        return new RankedCandidateDto(
                candidate.id(),
                candidate.personId(),
                candidate.fullName(),
                candidate.title(),
                candidate.companyName(),
                candidate.companyLogoUrl(),
                candidate.enrichedAt(),
                candidate.statusLabel(),
                candidate.statusColour(),
                candidate.pipelineStage() == null ? null : candidate.pipelineStage().label(),
                candidate.statusOutOfProcess(),
                standing.processingRestricted(),
                assessment);
    }
}
