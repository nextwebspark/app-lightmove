package app.lightmove.api.enrichment.sourcing.dto;

import java.util.List;
import java.util.UUID;

/**
 * What Add did: the people now executives of the position, those it already held, those whose profile the cache
 * has let go (a new search shows them again), those no search of this position found, and those withheld — on the
 * suppression list, or their processing restricted.
 */
public record AddSuggestionsResponse(int added, int alreadyOnPosition, int expired, int notFound, int withheld,
                                     List<Added> filed) {

    public record Added(String linkedinSlug, UUID triageCompanyId, UUID candidateId) {}
}
