package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.ErasedPerson;
import app.lightmove.api.candidate.service.PersonErasureContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The vendor cache's copy of an erased person's profile, forgotten across every workspace (the cache is shared): kept,
 * the next capture would fill them back in for free; forgetting it costs another workspace one paid lookup at most.
 */
@Component
@RequiredArgsConstructor
class PersonErasureVendorRecords implements PersonErasureContributor {

    private final CachedPeopleStore cache;

    @Override
    public void erase(ErasedPerson erased) {
        if (erased.profileSlug() != null) {
            cache.forget(erased.profileSlug());
        }
    }
}
