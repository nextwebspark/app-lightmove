package app.lightmove.api.triagecompany.service;

import app.lightmove.api.project.service.ProjectCompanyCounter;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.model.TriageCompanyCount;
import app.lightmove.api.triagecompany.repository.TriageCompanyRepository;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The projects list's "Companies" number, answered for {@code project}, which owns
 * {@link ProjectCompanyCounter}; this only implements it — the same inversion
 * {@code TriagedCompanyLookupAdapter} uses, and for the same reason.
 *
 * <p>Declined companies are left out. The number states the universe a mandate is still working, and
 * a mandate that took forty companies to reject thirty-nine of them has one — the rejections live on
 * the Declined page, which is where a consultant goes to count them.
 */
@Component
@RequiredArgsConstructor
class ProjectCompanyCounterAdapter implements ProjectCompanyCounter {

    private final TriageCompanyRepository triaged;

    @Override
    public Map<UUID, Long> countByProject(Collection<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return triaged.countByProjectIdInExcludingStatus(projectIds, TriageCompanyStatus.DECLINED)
                .stream()
                .collect(Collectors.toMap(TriageCompanyCount::projectId, TriageCompanyCount::total));
    }
}
