package app.lightmove.api.strategy.model;

import java.util.List;

/**
 * A read for companies sharing an anchor's niche: what they share is scored by how rare each keyword
 * is, so "luxury watches" outweighs "retail". An empty list places no constraint; a null bound none
 * on its side. {@code minShared} is how many of the anchor's distinctive keywords a company must
 * carry, held to however many the anchor has.
 */
public record SimilarityScope(List<String> anchorKeywords, List<String> countries, List<String> industries,
                              Long minEmployees, Long maxEmployees, List<String> excludedAccountIds,
                              int minShared, int limit) {

    public SimilarityScope {
        anchorKeywords = List.copyOf(anchorKeywords);
        countries = List.copyOf(countries);
        industries = List.copyOf(industries);
        excludedAccountIds = List.copyOf(excludedAccountIds);
    }
}
