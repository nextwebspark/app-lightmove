package app.lightmove.api.strategy.model;

import java.util.List;

/** Companies sharing an anchor's niche keywords. An empty list or a null bound places no constraint. */
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
