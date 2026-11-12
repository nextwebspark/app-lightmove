package app.lightmove.api.enrichment.sourcing.dto;

import app.lightmove.api.enrichment.peoplesearch.dto.PersonResultDto;
import app.lightmove.api.enrichment.sourcing.constant.FoundBy;
import app.lightmove.api.enrichment.sourcing.constant.SuggestionBand;
import app.lightmove.api.enrichment.sourcing.constant.SuggestionVerdict;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** What the latest search of each company found, best first, for the panel to review and add from. */
public record ExecutiveSuggestionsResponse(List<CompanySuggestions> companies) {

    /**
     * One company's latest search: when, its result, and the people in rank order. {@code expired} when the people
     * cache has let them go, so only a new search can show them again.
     */
    public record CompanySuggestions(UUID triageCompanyId, String companyName, UUID runId, Instant searchedAt,
                                     SourcingCompanyOutcomeResponse result, boolean expired,
                                     List<SuggestedPerson> people) {}

    /**
     * One person found: the verdict, the score and band for someone who fits, the reason codes, which index found
     * them, the executive they already are on this position ({@code candidateId}, null if not), and their profile as
     * read — null once the cache has let it go.
     */
    public record SuggestedPerson(String linkedinSlug, SuggestionVerdict verdict, Integer score, SuggestionBand band,
                                  int rank, List<String> reasons, FoundBy foundBy, UUID candidateId,
                                  PersonResultDto person) {}
}
