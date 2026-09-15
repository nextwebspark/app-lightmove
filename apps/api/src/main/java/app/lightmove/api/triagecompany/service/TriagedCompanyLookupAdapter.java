package app.lightmove.api.triagecompany.service;

import app.lightmove.api.strategy.service.TriagedCompanyLookup;
import app.lightmove.api.triagecompany.repository.TriageCompanyRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The one place {@code triagecompany} reaches back into {@code strategy}'s search: answers the ids a
 * project has already triaged, so {@code strategy} can exclude them without importing anything of
 * ours. {@code strategy} owns {@link TriagedCompanyLookup}; this only implements it.
 */
@Component
@RequiredArgsConstructor
class TriagedCompanyLookupAdapter implements TriagedCompanyLookup {

    private final TriageCompanyRepository triaged;

    @Override
    public List<String> accountIdsFor(UUID projectId) {
        return triaged.findApolloAccountIdsByProjectId(projectId);
    }
}
