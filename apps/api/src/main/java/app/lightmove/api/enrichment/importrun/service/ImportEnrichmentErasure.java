package app.lightmove.api.enrichment.importrun.service;

import app.lightmove.api.candidate.model.ErasedPerson;
import app.lightmove.api.candidate.service.PersonErasureContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** An erased person's queued or finished import-enrichment rows go with them: each names their profile. */
@Component
@RequiredArgsConstructor
class ImportEnrichmentErasure implements PersonErasureContributor {

    private final ImportEnrichmentItemStore store;

    @Override
    public void erase(ErasedPerson person) {
        store.erasePerson(person.workspaceId(), person.profileSlug(), person.candidateIds());
    }
}
