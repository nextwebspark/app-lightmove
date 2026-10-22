package app.lightmove.api.notetaker.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.notetaker.model.CompetencyEvidence;
import app.lightmove.api.notetaker.model.MeetingSummary;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MeetingSummaryScorerTest {

    @Test
    @DisplayName("counts the facts carried, the points mentioned, what was invented and the competencies placed")
    void scores() {
        MeetingSummary summary = new MeetingSummary("Priya led a finance transformation and wants a group remit.",
                List.of("Raised USD 400m in debt"), "Wants a group-level remit", "AED 1.2m base", "Three months", null,
                List.of(), List.of("Send the brief"),
                List.of(new CompetencyEvidence("Capital markets", "Led the debt raise", "00:02:10")),
                List.of("Stakeholder management"), List.of());
        MeetingSummaryScorer.Expected expected = new MeetingSummaryScorer.Expected(
                Map.of("motivation", List.of("group"), "notice", List.of("three months", "90 days"),
                        "mobility", List.of("relocate")),
                List.of("400m", "finance transformation"), List.of("AED 2m", "signed"),
                List.of("Capital markets"), List.of("Stakeholder management"));

        MeetingSummaryScorer.Score score = MeetingSummaryScorer.score(expected, summary);

        assertThat(score).isEqualTo(new MeetingSummaryScorer.Score(3, 2, 2, 2, 0, 2, 2));
        assertThat(MeetingSummaryScorer.markdown("synthetic", "gemini", "2026-10-22", 1, score))
                .contains("| 1 | 2 / 3 | 2 / 2 | 0 | 2 / 2 |");
    }

    @Test
    @DisplayName("the committed synthetic calls read as the eval reads a private set")
    void syntheticCallsRead() {
        List<MeetingSummaryEval.GoldenCall> calls = MeetingSummaryEval.readGolden(MeetingSummaryEval.SYNTHETIC);

        assertThat(calls).extracting(MeetingSummaryEval.GoldenCall::id).containsExactly("cfo-screen", "no-position");
        assertThat(calls.getFirst().transcript()).hasSize(6);
        assertThat(calls.getFirst().briefOrNull().competencies().competencies()).hasSize(2);
        assertThat(calls.getLast().briefOrNull()).isNull();
        assertThat(calls.getFirst().expect().expected().facts()).containsKeys("motivation", "notice", "mobility");
    }
}
