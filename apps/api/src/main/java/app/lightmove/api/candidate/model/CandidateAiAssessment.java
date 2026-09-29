package app.lightmove.api.candidate.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;

/**
 * The model's reading of a candidate against the mandate's competencies, stored whole on the row
 * (V79) and replaced whole by the next run. {@code assessedAt} is an ISO instant, as
 * {@link CandidateProfile#enrichedAt} is.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CandidateAiAssessment(String summary, CompetencyPanelAssessment technical,
                                    CompetencyPanelAssessment behavioural, String assessedAt) {

    /** A summary with both panels unscored — what stands until the deep enrichment replaces it. */
    public static CandidateAiAssessment summaryOnly(String summary, Instant assessedAt) {
        CompetencyPanelAssessment unscored = new CompetencyPanelAssessment(null, List.of(), List.of());
        return new CandidateAiAssessment(summary, unscored, unscored, assessedAt.toString());
    }
}
