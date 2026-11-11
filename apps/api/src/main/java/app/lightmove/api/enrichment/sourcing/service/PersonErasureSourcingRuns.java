package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.candidate.model.ErasedPerson;
import app.lightmove.api.candidate.service.PersonErasureContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Find executives keeps who each run found by profile slug alone (V209 took the names off the runs that filed): an
 * erased person is taken off every run's results in the workspace.
 */
@Component
@RequiredArgsConstructor
class PersonErasureSourcingRuns implements PersonErasureContributor {

    private final SuggestionStore suggestions;

    @Override
    public void erase(ErasedPerson erased) {
        if (erased.profileSlug() != null) {
            suggestions.forget(erased.workspaceId(), erased.profileSlug());
        }
    }
}
