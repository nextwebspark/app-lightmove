package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.ErasedPerson;
import app.lightmove.api.candidate.service.PersonErasureContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What the vendors told us about an erased person's profile. The people cache is Uncava's and every workspace's, so
 * this reaches beyond the erasing workspace: kept, the next capture or search would fill the person back in from it
 * for free. Forgetting it costs another workspace one paid lookup at most.
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
