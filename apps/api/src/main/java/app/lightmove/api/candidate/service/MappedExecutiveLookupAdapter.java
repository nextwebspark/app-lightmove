package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.PipelineCatalog;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.MovedCompanyCount;
import app.lightmove.api.candidate.repository.SearchMatchedCandidate;
import app.lightmove.api.core.security.rbac.ProjectAudience;
import app.lightmove.api.core.text.model.SearchText;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.service.MappedExecutiveLookup;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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

    private static final Set<String> STAGE_NAMES = Arrays.stream(TriageCompanyStatus.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    private final CandidateRepository candidates;
    private final ProjectRepository projects;
    private final CandidatePipelineCatalog pipeline;

    @Override
    public Set<UUID> triageCompanyIdsMatchingExecutiveName(UUID projectId, TriageCompanyStatus stage,
                                                           String executiveName, ProjectAudience audience) {
        return candidates.findTriageCompanyIdsByProjectIdAndFullNameContainingIgnoreCase(
                projectId, stage, executiveName, audience.clientVisibleOnly());
    }

    @Override
    public Set<UUID> triageCompanyIdsWithExecutivesMatching(UUID projectId, TriageCompanyStatus stage,
                                                            SearchText search, ProjectAudience audience) {
        return candidates.findSearchMatches(projectId, search.likePattern(), audience == ProjectAudience.STAFF,
                        audience.clientVisibleOnly(), stage.name()).stream()
                .map(SearchMatchedCandidate::getTriageCompanyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** Status keys of the project's own workspace catalog; a key it does not hold matches nobody. */
    @Override
    public Set<UUID> triageCompanyIdsWithExecutiveStatusIn(UUID projectId, TriageCompanyStatus stage,
                                                           List<String> executiveStatuses, ProjectAudience audience) {
        return candidates.findTriageCompanyIdsByProjectIdAndStatusKeyIn(projectId, stage, executiveStatuses,
                audience.clientVisibleOnly());
    }

    @Override
    public Set<UUID> triageCompanyIdsWithExecutivesMovedTo(UUID projectId, TriageCompanyStatus stage,
                                                           ProjectAudience audience) {
        return candidates.findTriageCompanyIdsWithExecutivesMovedTo(projectId, stage.name(),
                audience.clientVisibleOnly());
    }

    @Override
    public Map<TriageCompanyStatus, Long> companyCountsWithExecutivesMoved(UUID projectId, ProjectAudience audience) {
        // A stage V160 retired, written by the previous revision while this one rolled out, is no stage to count.
        return candidates.countCompaniesWithExecutivesMovedByStage(projectId, audience.clientVisibleOnly()).stream()
                .filter(count -> STAGE_NAMES.contains(count.getTriageStatus()))
                .collect(Collectors.toMap(count -> TriageCompanyStatus.valueOf(count.getTriageStatus()),
                        MovedCompanyCount::getCompanies));
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
            String companyName, String executiveName, List<String> executiveStatuses, Set<UUID> withinIds,
            boolean ascending, Pageable pageable, ProjectAudience audience) {
        UUID[] within = withinIds == null ? null : withinIds.toArray(UUID[]::new);
        return executiveStatuses.isEmpty()
                ? candidates.findTriageCompanyIdsRankedByExecutiveStatus(
                        projectId, status.name(), companyName, executiveName, within, ascending,
                        audience.clientVisibleOnly(), pageable)
                : candidates.findTriageCompanyIdsRankedByExecutiveStatusAndExecutiveStatuses(
                        projectId, status.name(), companyName, executiveName, executiveStatuses,
                        within, ascending, audience.clientVisibleOnly(), pageable);
    }
}
