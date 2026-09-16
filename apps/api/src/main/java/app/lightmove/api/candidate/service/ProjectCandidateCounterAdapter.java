package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.CandidateCount;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.project.service.ProjectCandidateCounter;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The projects list's "Candidates" number, answered for {@code project}, which owns
 * {@link ProjectCandidateCounter}; this only implements it. Every executive the mandate has mapped,
 * whatever their status and whether or not they sit at one of its companies.
 */
@Component
@RequiredArgsConstructor
class ProjectCandidateCounterAdapter implements ProjectCandidateCounter {

    private final CandidateRepository candidates;

    @Override
    public Map<UUID, Long> countByProject(Collection<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return candidates.countByProjectIdIn(projectIds).stream()
                .collect(Collectors.toMap(CandidateCount::projectId, CandidateCount::total));
    }
}
