package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.service.MappedExecutiveLookup;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * The one place {@code candidate} reaches back into {@code triagecompany}'s Companies grid: answers
 * the executive-level facts its filters and its Status-rank sort need. {@code triagecompany} owns
 * {@link MappedExecutiveLookup}; this only implements it, and stays package-private the way
 * {@code TriagedCompanyLookupAdapter} does on {@code triagecompany}'s own side of the same pattern.
 */
@Component
@RequiredArgsConstructor
class MappedExecutiveLookupAdapter implements MappedExecutiveLookup {

    private final CandidateRepository candidates;

    @Override
    public Set<UUID> triageCompanyIdsMatchingExecutiveName(UUID projectId, String executiveName) {
        return candidates.findTriageCompanyIdsByProjectIdAndFullNameContainingIgnoreCase(
                projectId, executiveName);
    }

    /**
     * {@code CandidateStatus::valueOf} fails loud on a name it does not recognise — unlike
     * {@code CandidateRepository}'s ranking {@code CASE} and
     * {@code TriageCompanyService.EXECUTIVE_STATUS_TOKENS}, which mirror the same enum-name spelling as
     * string literals and would degrade silently instead. A rename should grep for all three regardless.
     */
    @Override
    public Set<UUID> triageCompanyIdsWithExecutiveStatusIn(UUID projectId, List<String> executiveStatuses) {
        List<CandidateStatus> statuses = executiveStatuses.stream().map(CandidateStatus::valueOf).toList();
        return candidates.findTriageCompanyIdsByProjectIdAndStatusIn(projectId, statuses);
    }

    @Override
    public Page<UUID> triageCompanyIdsRankedByExecutiveStatus(UUID projectId, TriageCompanyStatus status,
            String companyName, String executiveName, List<String> executiveStatuses,
            boolean ascending, Pageable pageable) {
        return executiveStatuses.isEmpty()
                ? candidates.findTriageCompanyIdsRankedByExecutiveStatus(
                        projectId, status.name(), companyName, executiveName, ascending, pageable)
                : candidates.findTriageCompanyIdsRankedByExecutiveStatusAndExecutiveStatuses(
                        projectId, status.name(), companyName, executiveName, executiveStatuses,
                        ascending, pageable);
    }
}
