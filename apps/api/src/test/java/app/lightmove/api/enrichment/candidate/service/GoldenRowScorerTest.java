package app.lightmove.api.enrichment.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.enrichment.candidate.model.GoldenRow;
import app.lightmove.api.enrichment.candidate.model.GoldenRowPrediction;
import app.lightmove.api.enrichment.candidate.service.GoldenRowScorer.EvalScore;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The eval's arithmetic, over the committed synthetic rows and canned predictions — no model. */
class GoldenRowScorerTest {

    private static final List<GoldenRow> ROWS = List.of(
            row("r1", "Western expat", "C-Suite"),
            row("r2", "Unknown", "N-1"),
            row("r3", "Emirati", "N-2"),
            row("r4", "Other expat", "N-1"),
            row("r5", "Asian", null));

    @Test
    @DisplayName("accuracy, precision at high, the GCC false positive and coverage are counted as defined")
    void metricsAreCounted() {
        EvalScore score = GoldenRowScorer.score(ROWS, List.of(
                new GoldenRowPrediction("r1", "western expat", "high", "C-Suite"),
                new GoldenRowPrediction("r2", "Saudi", "high", "N-2"),
                new GoldenRowPrediction("r3", "Emirati", "medium", "N-3"),
                new GoldenRowPrediction("r4", "Turkish", "low", null),
                new GoldenRowPrediction("r5", "Asian", "high", "N-1")));

        assertThat(score.rows()).isEqualTo(5);
        assertThat(score.accuracy()).isEqualTo(3 / 5.0);
        assertThat(score.precisionAtHigh()).isEqualTo(2 / 3.0);
        assertThat(score.gccFalsePositives()).isEqualTo(1);
        assertThat(score.nonGccRows()).isEqualTo(4);
        // "Turkish" is no group's spelling, so it is scored as an Unknown answer.
        assertThat(score.unknownRate()).isEqualTo(1 / 5.0);
        assertThat(score.coverage()).isEqualTo(4 / 5.0);
        assertThat(score.seniorityRows()).isEqualTo(4);
        assertThat(score.seniorityExact()).isEqualTo(1 / 4.0);
        assertThat(score.seniorityWithinOne()).isEqualTo(3 / 4.0);
        assertThat(score.confusion().get("Unknown").get("Saudi")).isEqualTo(1);
        assertThat(score.byCategory()).filteredOn(category -> category.category().equals("Emirati"))
                .singleElement()
                .satisfies(emirati -> assertThat(emirati.recall()).isEqualTo(1.0));
    }

    @Test
    @DisplayName("a subject that states no confidence reports n/a rather than a precision it never claimed")
    void aBaselineWithoutConfidenceIsNotApplicable() {
        EvalScore score = GoldenRowScorer.score(ROWS, List.of(new GoldenRowPrediction("r1", "Western expat", null, null)));

        assertThat(score.statesConfidence()).isFalse();
        assertThat(score.coverage()).isNull();
        assertThat(GoldenRowScorer.markdown("baseline", "synthetic", "2026-09-27", score))
                .contains("n/a — states no confidence")
                .doesNotContain("r1");
    }

    @Test
    @DisplayName("the committed synthetic fixtures parse and every label is in the vocabulary")
    void syntheticFixturesAreWellFormed() {
        List<GoldenRow> synthetic = GoldenRows.typed(GoldenRows.synthetic());

        assertThat(synthetic).hasSizeGreaterThanOrEqualTo(10);
        assertThat(synthetic).allSatisfy(golden -> {
            assertThat(golden.expected().nationality().equals(GoldenRowScorer.UNKNOWN)
                    || !GoldenRowScorer.canonical(golden.expected().nationality()).equals(GoldenRowScorer.UNKNOWN)).isTrue();
            assertThat(golden.profile().fullName()).isNotBlank();
        });
    }

    private static GoldenRow row(String id, String nationality, String seniority) {
        return new GoldenRow(id, "human_confirmed",
                new GoldenRow.GoldenProfile("Someone", null, null, null, null, null, null, null, null, null),
                new GoldenRow.GoldenLabel(nationality, seniority), null);
    }
}
