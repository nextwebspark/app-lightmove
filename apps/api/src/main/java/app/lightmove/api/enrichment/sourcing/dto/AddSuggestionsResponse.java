package app.lightmove.api.enrichment.sourcing.dto;

import java.util.List;
import java.util.UUID;

/**
 * What Add did: the people now executives of the position, those it already held, those whose profile the cache
 * has let go (a new search shows them again), and those no search of this position found.
 */
public record AddSuggestionsResponse(int added, int alreadyOnPosition, int expired, int notFound,
                                     List<Added> filed) {

    public record Added(String linkedinSlug, UUID triageCompanyId, UUID candidateId) {}
}
