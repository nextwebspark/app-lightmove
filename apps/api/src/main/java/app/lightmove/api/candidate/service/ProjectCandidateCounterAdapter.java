package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.CandidateCount;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.project.model.CandidateCountScope;
import app.lightmove.api.project.service.ProjectCandidateCounter;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The projects list's people-side numbers, answered for {@code project}, which owns
 * {@link ProjectCandidateCounter}; this only implements it.
 *
 * <p>Executives who have left the running are left out of "Candidates", the same rule
 * {@code ProjectCompanyCounterAdapter} applies to declined companies. The two numbers sit side by
 * side under one "Pipeline" heading and read as a pair, so counting everyone here and only the live
 * companies there would make one heading mean two things. Every executive ever mapped, ruled out or
 * not, is still on the Candidates screen — the closed stage keeps them deliberately, because the same
 * name comes up on the next mandate. Engaged and reached out are read by stage, so a workspace's own
 * statuses count where its admin put them. A mandate its caller reads as a client seat counts only the
 * executives shown to the client.
 */
@Component
@RequiredArgsConstructor
class ProjectCandidateCounterAdapter implements ProjectCandidateCounter {

    private final CandidateRepository candidates;
    private final CandidatePipelineCatalog pipeline;

    @Override
    public Map<UUID, Long> countByProject(CandidateCountScope scope) {
        return counted(scope, candidates::countStillInTheRunningByProjectIdIn);
    }

    @Override
    public Map<UUID, Long> countMappedByProject(CandidateCountScope scope) {
        return counted(scope, candidates::countEveryoneByProjectIdIn);
    }

    @Override
    public Map<UUID, Long> countEngagedByProject(CandidateCountScope scope) {
        if (scope.projectIds().isEmpty()) {
            return Map.of();
        }
        int interestedFrom = pipeline.progressAnchors(scope.workspaceId()).interestedFrom();
        return counted(scope, (projectIds, clientVisibleOnly) ->
                candidates.countEngagedByProjectIdIn(projectIds, interestedFrom, clientVisibleOnly));
    }

    @Override
    public Map<UUID, Long> countReachedOutByProject(CandidateCountScope scope) {
        if (scope.projectIds().isEmpty()) {
            return Map.of();
        }
        int identifiedAt = pipeline.progressAnchors(scope.workspaceId()).startingAt();
        return counted(scope, (projectIds, clientVisibleOnly) ->
                candidates.countReachedOutByProjectIdIn(projectIds, identifiedAt, clientVisibleOnly));
    }

    @Override
    public Map<UUID, Long> countMappedCompaniesByProject(CandidateCountScope scope) {
        return counted(scope, (projectIds, clientVisibleOnly) -> candidates.countMappedCompaniesByProjectIdIn(
                projectIds, TriageCompanyStatus.DECLINED.name(), clientVisibleOnly));
    }

    private static Map<UUID, Long> counted(CandidateCountScope scope,
                                           BiFunction<Collection<UUID>, Boolean, List<CandidateCount>> count) {
        Map<UUID, Long> totals = new HashMap<>();
        List<UUID> whole = scope.countedWhole();
        if (!whole.isEmpty()) {
            count.apply(whole, false).forEach(row -> totals.put(row.getProjectId(), row.getTotal()));
        }
        List<UUID> clientVisibleOnly = scope.countedClientVisibleOnly();
        if (!clientVisibleOnly.isEmpty()) {
            count.apply(clientVisibleOnly, true).forEach(row -> totals.put(row.getProjectId(), row.getTotal()));
        }
        return totals;
    }
}
