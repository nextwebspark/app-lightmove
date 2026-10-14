package app.lightmove.api.candidate.model;

import app.lightmove.api.common.constant.CompetencyPanel;
import java.util.Locale;

/**
 * One criterion's score as stored, with the criterion it was given against as it stood then. The brief's
 * competencies have no stable id — each save of that step replaces the list — so the score is matched back
 * on panel and name, and keeps its own weight for the day the brief drops the criterion.
 *
 * @param score 0–10, or null: scored nothing yet, which is an open question rather than a zero
 */
public record CriterionScore(CompetencyPanel panel, String name, int weight, Integer score, String evidence) {

    public String matchKey() {
        return keyOf(panel, name);
    }

    public boolean isEmpty() {
        return score == null && (evidence == null || evidence.isBlank());
    }

    public static String keyOf(CompetencyPanel panel, String name) {
        return panel.name() + ":" + name.strip().toLowerCase(Locale.ROOT);
    }
}
