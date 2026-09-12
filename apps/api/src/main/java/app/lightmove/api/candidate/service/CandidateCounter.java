package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.CandidateCount;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.project.service.ProjectCandidateCounter;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The projects list's "Candidates" number, answered for the {@code project} feature: every executive
 * the mandate has mapped, whatever their status and whether or not they sit at one of its companies.
 *
 * <p>Its own bean rather than a method on {@link CandidateService}, for the reason given on
 * {@code TriageCompanyCounter}.
 */
@Service
@RequiredArgsConstructor
public class CandidateCounter implements ProjectCandidateCounter {

    private final CandidateRepository candidates;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> countByProject(Collection<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return candidates.countByProjectIdIn(projectIds).stream()
                .collect(Collectors.toMap(CandidateCount::projectId, CandidateCount::total));
    }
}
