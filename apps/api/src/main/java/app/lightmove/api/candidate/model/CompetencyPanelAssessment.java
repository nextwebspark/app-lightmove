package app.lightmove.api.candidate.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** One competency panel's reading of a candidate: a 1–10 score (null when unjudgeable) and why. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CompetencyPanelAssessment(Integer score, List<String> positives, List<String> negatives) {

    public CompetencyPanelAssessment {
        positives = positives == null ? List.of() : List.copyOf(positives);
        negatives = negatives == null ? List.of() : List.copyOf(negatives);
    }
}
