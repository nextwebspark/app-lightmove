package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.PipelineCatalog;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.core.security.rbac.ProjectAudience;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.service.MappedExecutiveLookup;
import java.util.Collection;
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
    private final ProjectRepository projects;
    private final CandidatePipelineCatalog pipeline;

    @Override
    public Set<UUID> triageCompanyIdsMatchingExecutiveName(UUID projectId, String executiveName,
                                                           ProjectAudience audience) {
        return candidates.findTriageCompanyIdsByProjectIdAndFullNameContainingIgnoreCase(
                projectId, executiveName, audience.clientVisibleOnly());
    }

    /** Status keys of the project's own workspace catalog; a key it does not hold matches nobody. */
    @Override
    public Set<UUID> triageCompanyIdsWithExecutiveStatusIn(UUID projectId, List<String> executiveStatuses,
                                                           ProjectAudience audience) {
        return candidates.findTriageCompanyIdsByProjectIdAndStatusKeyIn(projectId, executiveStatuses,
                audience.clientVisibleOnly());
    }

    @Override
    public List<String> unknownExecutiveStatuses(UUID workspaceId, UUID projectId,
                                                 Collection<String> executiveStatuses) {
        projects.requireInWorkspace(projectId, workspaceId);
        PipelineCatalog catalog = pipeline.catalogOf(workspaceId);
        return executiveStatuses.stream().filter(key -> catalog.byKey(key).isEmpty()).toList();
    }

    @Override
    public Page<UUID> triageCompanyIdsRankedByExecutiveStatus(UUID projectId, TriageCompanyStatus status,
            String companyName, String executiveName, List<String> executiveStatuses,
            boolean ascending, Pageable pageable, ProjectAudience audience) {
        return executiveStatuses.isEmpty()
                ? candidates.findTriageCompanyIdsRankedByExecutiveStatus(
                        projectId, status.name(), companyName, executiveName, ascending,
                        audience.clientVisibleOnly(), pageable)
                : candidates.findTriageCompanyIdsRankedByExecutiveStatusAndExecutiveStatuses(
                        projectId, status.name(), companyName, executiveName, executiveStatuses,
                        ascending, audience.clientVisibleOnly(), pageable);
    }
}
