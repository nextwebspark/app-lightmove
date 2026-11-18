package app.lightmove.api.universe.model;

import java.util.List;
import org.springframework.modulith.NamedInterface;

/** Companies sharing an anchor's niche keywords. An empty list or a null bound places no constraint. */
@NamedInterface(name = "api", propagate = false)
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
