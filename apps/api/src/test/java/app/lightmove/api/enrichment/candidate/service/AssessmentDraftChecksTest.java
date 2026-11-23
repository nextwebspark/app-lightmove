package app.lightmove.api.enrichment.candidate.service;

import static app.lightmove.api.enrichment.candidate.service.AssessmentCaseFixture.answer;
import static app.lightmove.api.enrichment.candidate.service.AssessmentCaseFixture.expected;
import static app.lightmove.api.enrichment.candidate.service.AssessmentCaseFixture.input;
import static app.lightmove.api.enrichment.candidate.service.AssessmentCaseFixture.scoreOf;
import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.llm.constant.EvalCheckVerdict;
import app.lightmove.api.core.llm.model.EvalReading;
import app.lightmove.api.core.llm.service.EvalCheck;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** #1326: every rule passes a careful answer and fails the one made worse in its own way. */
class AssessmentDraftChecksTest {

    @Test
    @DisplayName("a careful answer passes every rule")
    void aCarefulAnswerPasses() {
        assertThat(verdicts(expected(), answer()).values()).containsOnly(EvalCheckVerdict.PASSED);
    }

    @Test
    @DisplayName("an answer that could not be read fails the first rule and gives the rest nothing to judge")
    void noAnswerIsOneFailure() {
        Map<String, EvalCheckVerdict> verdicts = verdicts(expected(), null);

        assertThat(verdicts.get("competency-ids")).isEqualTo(EvalCheckVerdict.FAILED);
        assertThat(verdicts.entrySet().stream().filter(verdict -> !verdict.getKey().equals("competency-ids")))
                .allSatisfy(verdict -> assertThat(verdict.getValue()).isEqualTo(EvalCheckVerdict.NOT_APPLICABLE));
    }

    @Test
    @DisplayName("an invented id, a repeated one or a competency left out fails the ids")
    void idsMustBeTheBriefs() {
        assertFails("competency-ids", answer -> scoreOf(answer, "T2").put("id", "T9"));
        assertFails("competency-ids", answer -> scoreOf(answer, "T2").put("id", "T1"));
        assertFails("competency-ids", answer -> ((ArrayNode) answer.get("scores")).remove(1));
    }

    @Test
    @DisplayName("a score out of range, a fraction, or evidence beside no score fails the range")
    void scoresMustBeWholeAndInRange() {
        assertFails("scores-in-range", answer -> scoreOf(answer, "T1").put("score", 12));
        assertFails("scores-in-range", answer -> scoreOf(answer, "T1").put("score", 7.5));
        assertFails("scores-in-range",
                answer -> scoreOf(answer, "T2").put("evidence", "Led public-company reporting at scale."));
        assertThat(verdictOf("scores-in-range", expected(), changed(
                answer -> scoreOf(answer, "T2").put("evidence", "Nothing on file bears on this."))))
                .isEqualTo(EvalCheckVerdict.PASSED);
    }

    @Test
    @DisplayName("evidence naming no source, or a strong score resting on another source than labelled, fails")
    void evidenceMustNameItsSource() {
        assertFails("evidence-names-source", answer -> scoreOf(answer, "B1")
                .put("evidence", "She moved both finance teams onto one close calendar in four months."));
        assertFails("evidence-names-source", answer -> scoreOf(answer, "T1")
                .put("evidence", "Her profile shows she has refinanced debt as finance chief."));
        assertThat(verdictOf("evidence-names-source", expected(), changed(answer -> scoreOf(answer, "B2")
                .put("evidence", "According to the March 2024 board paper she set a treasury policy."))))
                .as("a document is named by its title's words in any order").isEqualTo(EvalCheckVerdict.PASSED);
        assertThat(verdictOf("evidence-names-source", expected(), changed(answer -> {
            scoreOf(answer, "T1").put("evidence", "As recorded in the call on 14 Oct 2026, she ran the refinancing.");
            scoreOf(answer, "B1").put("evidence", "In her interview on 20 Oct 2026 (03:10 in) she describes it.");
            scoreOf(answer, "B2").put("evidence", "There is no evidence on file of how a board regards her.");
        }))).as("the forms the model writes, and a score explained by nothing being on file")
                .isEqualTo(EvalCheckVerdict.PASSED);
    }

    @Test
    @DisplayName("a figure, a date, a call time or a quotation the case does not hold fails the grounding")
    void evidenceMustBeInTheInput() {
        assertFails("evidence-in-input", answer -> scoreOf(answer, "T1").put("evidence",
                "A call note of 14 Oct 2026 records a USD 450m refinancing."));
        assertFails("evidence-in-input", answer -> scoreOf(answer, "T1").put("evidence",
                "A note of 3 Sep 2026 records the refinancing."));
        assertFails("evidence-in-input", answer -> scoreOf(answer, "B1").put("evidence",
                "On the call 20 Oct 2026, 09:59 in, she describes the integration."));
        assertFails("evidence-in-input", answer -> scoreOf(answer, "B1").put("evidence",
                "On the call 20 Oct 2026, 03:10 in, she says \"I closed the books in three days flat\"."));
        assertThat(verdictOf("evidence-in-input", expected(), changed(answer -> scoreOf(answer, "B1").put("evidence",
                "On the call 20 Oct 2026, 03:10 in, she says \"I moved both finance teams onto one close "
                        + "calendar\"."))))
                .isEqualTo(EvalCheckVerdict.PASSED);
    }

    @Test
    @DisplayName("an email or a phone number anywhere in the answer fails, the planted ones however they are spaced")
    void noContactComesBack() {
        assertFails("no-contact", answer -> ((ObjectNode) answer.get("report"))
                .put("motivation", "Write to her at " + AssessmentCaseFixture.EMAIL + "."));
        assertFails("no-contact", answer -> scoreOf(answer, "B2")
                .put("evidence", "Inferred from her career; call her on +97150 5550147."));
        assertFails("no-contact", answer -> ((ObjectNode) answer.get("report"))
                .put("concerns", "Ask on someone.else@example.org."));
    }

    @Test
    @DisplayName("a colleague's claim repeated as evidence, or a call the executive cannot be told apart in, fails")
    void nothingIsTakenFromOthers() {
        assertFails("no-team-evidence", answer -> scoreOf(answer, "B1").put("evidence",
                "On the call 20 Oct 2026, 03:10 in, she has run three turnarounds."));

        ObjectNode unusable = expected();
        unusable.putArray("unusableCalls").add("2026-10-20");
        assertThat(verdictOf("no-team-evidence", unusable, answer())).isEqualTo(EvalCheckVerdict.FAILED);

        ObjectNode nothingLabelled = expected();
        nothingLabelled.remove("notEvidence");
        assertThat(verdictOf("no-team-evidence", nothingLabelled, answer()))
                .isEqualTo(EvalCheckVerdict.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("a score outside the band and its tolerance, or a score where none is expected, fails the band")
    void scoresMustBeInTheirBand() {
        assertFails("score-in-band", answer -> scoreOf(answer, "T1").put("score", 5));
        assertFails("score-in-band", answer -> scoreOf(answer, "T2").put("score", 6));
        assertFails("score-in-band", answer -> scoreOf(answer, "B1").putNull("score"));
        assertThat(verdictOf("score-in-band", expected(), changed(answer -> scoreOf(answer, "T1").put("score", 6))))
                .as("one below the band is inside the default tolerance").isEqualTo(EvalCheckVerdict.PASSED);
    }

    @Test
    @DisplayName("a competency evidenced by self-report alone fails above 7")
    void selfReportStaysCapped() {
        assertFails("self-report-cap", answer -> scoreOf(answer, "B2").put("score", 8));
    }

    @Test
    @DisplayName("a run reads each competency off an answer: its name, the label in words, the score and evidence")
    void anAnswerIsReadCompetencyByCompetency() {
        List<EvalReading> readings = AssessmentDraftChecks.readingsOf(input(), expected(),
                changed(answer -> scoreOf(answer, "B1").put("score", 3)));

        assertThat(readings).extracting(EvalReading::key).containsExactly("T1", "T2", "B1", "B2");
        EvalReading capitalMarkets = readings.getFirst();
        assertThat(capitalMarkets.label()).isEqualTo("Capital markets");
        assertThat(capitalMarkets.expected()).isEqualTo("7 to 9");
        assertThat(capitalMarkets.score()).isEqualTo(8.0);
        assertThat(capitalMarkets.text()).startsWith("A call note of 14 Oct 2026");
        assertThat(capitalMarkets.outside()).isFalse();
        assertThat(readings.get(1).expected()).isEqualTo("No evidence");
        assertThat(readings.get(1).score()).isNull();
        assertThat(readings.get(1).outside()).isFalse();
        assertThat(readings.get(2).outside()).as("3 is below the band of 6 to 8 and its tolerance").isTrue();
        assertThat(AssessmentDraftChecks.readingsOf(input(), expected(), null))
                .as("an answer that could not be read scores nothing, which a band counts as outside")
                .extracting(EvalReading::outside).containsExactly(true, false, true, true);
    }

    private static void assertFails(String checkId, Consumer<ObjectNode> worsened) {
        assertThat(verdictOf(checkId, expected(), changed(worsened))).as(checkId).isEqualTo(EvalCheckVerdict.FAILED);
    }

    private static ObjectNode changed(Consumer<ObjectNode> change) {
        ObjectNode answer = answer();
        change.accept(answer);
        return answer;
    }

    private static EvalCheckVerdict verdictOf(String checkId, JsonNode expected, JsonNode answer) {
        return verdicts(expected, answer).get(checkId);
    }

    private static Map<String, EvalCheckVerdict> verdicts(JsonNode expected, JsonNode answer) {
        Map<String, EvalCheckVerdict> verdicts = new LinkedHashMap<>();
        for (EvalCheck check : AssessmentDraftChecks.ALL) {
            verdicts.put(check.id(), check.verdictOn(input(), expected, answer));
        }
        return verdicts;
    }
}
