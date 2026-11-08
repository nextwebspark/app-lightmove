package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.candidate.model.ErasedPerson;
import app.lightmove.api.candidate.service.PersonErasureContributor;
import app.lightmove.api.enrichment.sourcing.repository.ExecutiveSourcingRunRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Find executives keeps who each run filed, by name, on the run itself. An erased person's picks keep their count and
 * lose their name, reason and row.
 */
@Component
@RequiredArgsConstructor
class PersonErasureSourcingRuns implements PersonErasureContributor {

    private final ExecutiveSourcingRunRepository runs;

    @Override
    public void erase(ErasedPerson erased) {
        if (erased.candidateIds().isEmpty()) {
            return;
        }
        Set<UUID> candidateIds = new HashSet<>(erased.candidateIds());
        runs.findByWorkspaceIdAndProjectIdIn(erased.workspaceId(), erased.projectIds())
                .forEach(run -> run.forgetPicksOf(candidateIds));
    }
}
