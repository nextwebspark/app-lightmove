package app.uncava.api.triagecompany.service;

import app.uncava.api.project.service.ProjectCompanyCounter;
import app.uncava.api.triagecompany.constant.TriageCompanyStatus;
import app.uncava.api.triagecompany.model.TriageCompanyCount;
import app.uncava.api.triagecompany.repository.TriageCompanyRepository;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The projects list's "Companies" number for {@code project}; declined companies are not counted. */
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
