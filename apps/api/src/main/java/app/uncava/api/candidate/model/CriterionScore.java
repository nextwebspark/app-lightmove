package app.uncava.api.candidate.model;

import app.uncava.api.common.constant.CompetencyPanel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Locale;
import java.util.Objects;

/**
 * One criterion's score as stored, with the criterion it was given against as it stood then. The brief's
 * competencies have no stable id — each save of that step replaces the list — so the score is matched back
 * on panel and name, and keeps its own weight for the day the brief drops the criterion.
 *
 * @param score 0–10, or null: scored nothing yet, which is an open question rather than a zero
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CriterionScore(CompetencyPanel panel, String name, int weight, Integer score, String evidence) {

    public String matchKey() {
        return keyOf(panel, name);
    }

    // Never isEmpty(): Hibernate's JSON mapper read it as an "empty" property, wrote it into the column and then
    // refused to read it back, so the first save with a score answered 500.
    public boolean holdsNothing() {
        return score == null && (evidence == null || evidence.isBlank());
    }

    /** Whether two scores say the same thing. The weight is the brief's, not a judgement, so it is not compared. */
    public boolean sameJudgementAs(CriterionScore other) {
        return other != null && Objects.equals(score, other.score) && Objects.equals(evidence, other.evidence);
    }

    public static String keyOf(CompetencyPanel panel, String name) {
        return panel.name() + ":" + name.strip().toLowerCase(Locale.ROOT);
    }
}
