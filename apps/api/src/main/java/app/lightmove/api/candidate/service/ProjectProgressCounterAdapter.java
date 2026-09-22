package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.model.CandidateEngagementCount;
import app.lightmove.api.candidate.model.CompanyCoverageCount;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.project.model.ProjectProgressCounts;
import app.lightmove.api.project.service.ProjectProgressCounter;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The numbers behind every mandate's progress bar and health pill, answered for {@code project},
 * which owns {@link ProjectProgressCounter}; this only implements it.
 *
 * <p>Two grouped queries for a whole workspace's list, however many mandates it holds.
 */
@Component
@RequiredArgsConstructor
class ProjectProgressCounterAdapter implements ProjectProgressCounter {

    /** Engaged enough to put in front of the client: a conversation that is going somewhere. */
    private static final Set<CandidateStatus> QUALIFIED =
            EnumSet.of(CandidateStatus.ENGAGED, CandidateStatus.INTERESTED);

    private final CandidateRepository candidates;

    @Override
    public Map<UUID, ProjectProgressCounts> countByProject(Collection<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        List<CompanyCoverageCount> coverage =
                candidates.countCoverageByProjectIdIn(projectIds, TriageCompanyStatus.DECLINED.name());
        Map<UUID, CandidateEngagementCount> engagement =
                candidates.countEngagementByProjectIdIn(projectIds, CandidateStatus.IDENTIFIED, QUALIFIED)
                        .stream()
                        .collect(Collectors.toMap(CandidateEngagementCount::projectId, Function.identity()));

        Map<UUID, ProjectProgressCounts> counted = new HashMap<>();
        for (CompanyCoverageCount row : coverage) {
            counted.put(row.getProjectId(), combine(row, engagement.remove(row.getProjectId())));
        }
        // A mandate whose people were all mapped at companies it never triaged has no coverage row.
        engagement.forEach((projectId, worked) -> counted.put(projectId, combine(null, worked)));
        return counted;
    }

    private static ProjectProgressCounts combine(CompanyCoverageCount coverage,
                                                 CandidateEngagementCount engagement) {
        return new ProjectProgressCounts(
                coverage == null ? 0 : coverage.getUniverseTotal(),
                coverage == null ? 0 : coverage.getResearched(),
                engagement == null ? 0 : engagement.total(),
                engagement == null ? 0 : engagement.pastIdentified(),
                engagement == null ? 0 : engagement.qualified());
    }
}
