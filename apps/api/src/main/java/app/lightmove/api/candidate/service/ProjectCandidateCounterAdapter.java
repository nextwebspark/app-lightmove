package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.model.CandidateCount;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.project.service.ProjectCandidateCounter;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
 * not, is still on the Candidates screen — the enum keeps them deliberately, because the same name
 * comes up on the next mandate.
 */
@Component
@RequiredArgsConstructor
class ProjectCandidateCounterAdapter implements ProjectCandidateCounter {

    /** The enum's own "three ways they leave the running". */
    private static final Set<CandidateStatus> LEFT_THE_RUNNING = EnumSet.of(
            CandidateStatus.NOT_INTERESTED, CandidateStatus.OFF_LIMITS, CandidateStatus.OUT_OF_SCOPE);

    private static final Set<CandidateStatus> ENGAGED = EnumSet.of(
            CandidateStatus.ENGAGED, CandidateStatus.INTERESTED);

    private final CandidateRepository candidates;

    @Override
    public Map<UUID, Long> countByProject(Collection<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return byProject(candidates.countByProjectIdInExcludingStatuses(projectIds, LEFT_THE_RUNNING));
    }

    @Override
    public Map<UUID, Long> countMappedByProject(Collection<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return byProject(candidates.countEveryoneByProjectIdIn(projectIds));
    }

    @Override
    public Map<UUID, Long> countEngagedByProject(Collection<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return byProject(candidates.countByProjectIdInAndStatusIn(projectIds, ENGAGED));
    }

    @Override
    public Map<UUID, Long> countMappedCompaniesByProject(Collection<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return byProject(candidates.countMappedCompaniesByProjectIdIn(
                projectIds, TriageCompanyStatus.DECLINED.name()));
    }

    private static Map<UUID, Long> byProject(List<CandidateCount> counts) {
        return counts.stream().collect(Collectors.toMap(CandidateCount::getProjectId, CandidateCount::getTotal));
    }
}
