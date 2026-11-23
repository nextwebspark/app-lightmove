package app.lightmove.api.enrichment.candidate.service;

import static app.lightmove.api.enrichment.candidate.service.AssessmentCaseFixture.JSON;
import static app.lightmove.api.enrichment.candidate.service.AssessmentCaseFixture.expected;
import static app.lightmove.api.enrichment.candidate.service.AssessmentCaseFixture.input;
import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.position.model.AssessmentBrief;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/** #1326: an assessment case's shape, and the material it becomes for the drafter. */
class AssessmentCaseTest {

    @Test
    @DisplayName("a well-formed case and its labels have no problem")
    void aWellFormedCaseIsAccepted() {
        assertThat(AssessmentCase.problemsOf(input(), JSON)).isEmpty();
        assertThat(AssessmentExpectation.problemsOf(input(), expected())).isEmpty();
    }

    @Test
    @DisplayName("each thing missing from a case is named where it is")
    void aMalformedCaseIsNamed() {
        ObjectNode input = input();
        ((ObjectNode) input.get("notes").get(1)).remove("date");
        ((ObjectNode) input.get("transcripts").get(0).get("lines").get(2)).put("at", "soon");
        ((ObjectNode) input.get("profile")).put("email", AssessmentCaseFixture.EMAIL);
        ((ObjectNode) input.get("brief").get("competencies").get(0)).put("weight", 140);
        ((ObjectNode) input.get("documents").get(0)).remove("text");
        input.put("salary", "USD 400k");

        assertThat(AssessmentCase.problemsOf(input, JSON)).containsExactlyInAnyOrder(
                "input holds a field a case does not have.",
                "input.profile holds a field a profile does not have. A case never carries a contact, a salary "
                        + "or a note.",
                "input.notes[1].date is missing. Every note carries its date.",
                "input.transcripts[0].lines[2].at is missing. Every line carries its time into the call, as 03:10.",
                "input.documents[0].text is missing.",
                "input.brief.competencies[0].weight is not a whole number from 0 to 100.");
    }

    @Test
    @DisplayName("labels must cover the brief's competencies, and name no other")
    void labelsMustMatchTheBrief() {
        ObjectNode expected = expected();
        ObjectNode labels = (ObjectNode) expected.get("competencies");
        labels.set("T7", labels.remove("T2"));
        ((ObjectNode) labels.get("T1")).putArray("band").add(9).add(7);
        ((ObjectNode) labels.get("B1")).put("restsOn", "hearsay");
        expected.putArray("unusableCalls").add("2026-01-01");

        assertThat(AssessmentExpectation.problemsOf(input(), expected)).containsExactlyInAnyOrder(
                "expected.competencies names a competency the case's brief does not list.",
                "expected.competencies.T2 is missing. Every competency of the brief is labelled.",
                "expected.competencies.T1.band is not two whole numbers from 0 to 10, the lower first.",
                "expected.competencies.B1.restsOn is not profile, note, call or document.",
                "expected.unusableCalls names a date no call of the case has.");
    }

    @Test
    @DisplayName("the material is written as a real person's is: newest first, contacts out, the team marked")
    void theMaterialIsWrittenAsProductionWritesIt() {
        MaterialText material = AssessmentCase.materialOf(input());

        assertThat(material.noteCount()).isEqualTo(2);
        assertThat(material.transcriptCount()).isEqualTo(1);
        assertThat(material.documentCount()).isEqualTo(1);
        assertThat(material.notes())
                .startsWith("- [Call · 14 Oct 2026 · Connected] Spoke for twenty minutes.")
                .contains("[email removed]").contains("[phone removed]")
                .doesNotContain(AssessmentCaseFixture.EMAIL).doesNotContain("555 0147")
                .contains("- [General · 2 Oct 2026] A former colleague");
        assertThat(material.transcripts())
                .startsWith("=== Call: First interview · 20 Oct 2026 ===\n")
                .contains("[00:40] Omar Nasser (your team): You have run three turnarounds, I hear.")
                .contains("[03:10] Layla Haddad: When we bought the two carriers")
                .contains("in four months. I am a strong leader.");
        assertThat(material.documents()).startsWith("=== Other: Board paper March 2024 ===\n");
    }

    @Test
    @DisplayName("the brief and its competency ids are the drafter's own")
    void theBriefIsRead() {
        AssessmentBrief brief = AssessmentCase.briefOf(input());

        assertThat(brief.seniority()).isEqualTo(Seniority.C_SUITE);
        assertThat(brief.competencies().technicalShare()).isEqualTo(60);
        assertThat(List.copyOf(AssessmentCase.competenciesOf(input()).keySet()))
                .containsExactly("T1", "T2", "B1", "B2");
        assertThat(AssessmentCase.dossierOf(input(), JSON).fullName()).isEqualTo("Layla Haddad");
    }
}
