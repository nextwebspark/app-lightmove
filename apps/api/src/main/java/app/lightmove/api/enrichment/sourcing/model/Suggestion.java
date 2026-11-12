package app.lightmove.api.enrichment.sourcing.model;

import app.lightmove.api.enrichment.sourcing.constant.FoundBy;
import app.lightmove.api.enrichment.sourcing.constant.SuggestionVerdict;
import java.util.List;

/**
 * One person a run found at a company, judged: the profile slug, the verdict, the score out of 100 for someone who
 * fits (null otherwise), their place in the company's list, the reason codes, and which index found them.
 */
public record Suggestion(String linkedinSlug, SuggestionVerdict verdict, Integer score, int rank, List<String> reasons,
                         FoundBy foundBy) {

    public Suggestion {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
