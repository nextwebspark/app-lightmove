package app.lightmove.api.enrichment.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.TestLlmCallPolicy;
import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.candidate.model.AiScorecardDraft;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.CandidateScorecard;
import app.lightmove.api.candidate.model.CriterionScore;
import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.common.constant.CriterionMode;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.position.model.AssessmentBrief;
import app.lightmove.api.position.model.BriefCompetencies;
import app.lightmove.api.position.model.BriefCompetency;
import app.lightmove.api.position.model.BriefCriterion;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

class CandidateScorecardDrafterTest {

    private static final CandidateDossier NOTHING_MISSING =
            CandidateAiEnricherTest.dossier(EnumSet.noneOf(BackgroundField.class));

    private static final AssessmentBrief BRIEF = new AssessmentBrief("Group CFO", Seniority.C_SUITE, "Finance",
            "Dubai", "United Arab Emirates", "Lead finance through the listing.", List.of("Own the IPO"),
            "Preparing to list", List.of("Capital markets readiness"),
            List.of(new BriefCriterion("Listed-company CFO", CriterionMode.REQUIRED)),
            new BriefCompetencies(List.of(
                    new BriefCompetency(CompetencyPanel.TECHNICAL, "Capital markets", "IPO and debt raising", 60),
                    new BriefCompetency(CompetencyPanel.TECHNICAL, "Treasury", null, 40),
                    new BriefCompetency(CompetencyPanel.BEHAVIOURAL, "Board presence", null, 100)), 70));

    @Test
    @DisplayName("each score lands on the competency its id names; an unknown or repeated id is let go")
    void scoresMapBackToTheBrief() {
        AiScorecardDraft draft = drafterOver(new RecordingChatModel("""
                {"scores":[
                  {"id":"T1","evidence":" Led a dairy IPO in 2019. ","score":8},
                  {"id":"t2","evidence":"Nothing on treasury in the profile.","score":null},
                  {"id":"T1","evidence":"A second answer.","score":2},
                  {"id":"T9","evidence":"Not in the brief.","score":5},
                  {"id":"B1","evidence":null,"score":null}],
                 "report":{"careerSummary":" Finance leader. ","coreValue":"IPO","concerns":null,
                           "workingStyle":"To confirm.","motivation":"  "}}"""))
                .draft(NOTHING_MISSING, BRIEF)
                .orElseThrow();

        assertThat(draft.scores()).containsExactly(
                new CriterionScore(CompetencyPanel.TECHNICAL, "Capital markets", 60, 8, "Led a dairy IPO in 2019."),
                new CriterionScore(CompetencyPanel.TECHNICAL, "Treasury", 40, null,
                        "Nothing on treasury in the profile."));
        assertThat(draft.report().careerSummary()).isEqualTo("Finance leader.");
        assertThat(draft.report().concerns()).isNull();
        assertThat(draft.report().motivation()).isNull();
    }

    @Test
    @DisplayName("a score off the 0–10 scale is no score, and overlong text is cut to what the tab holds")
    void outOfRangeIsDroppedAndTextIsBounded() {
        String longEvidence = "x".repeat(CandidateScorecard.MAX_EVIDENCE + 50);
        AiScorecardDraft draft = drafterOver(new RecordingChatModel("""
                {"scores":[{"id":"T1","evidence":"%s","score":11}],"report":null}""".formatted(longEvidence)))
                .draft(NOTHING_MISSING, BRIEF)
                .orElseThrow();

        assertThat(draft.scores()).singleElement().satisfies(score -> {
            assertThat(score.score()).isNull();
            assertThat(score.evidence()).hasSize(CandidateScorecard.MAX_EVIDENCE);
        });
    }

    @Test
    @DisplayName("the model reads the role, its criteria and each competency with its id, and nothing it should not")
    void theBriefReachesThePrompt() {
        RecordingChatModel model = new RecordingChatModel("{\"scores\":[],\"report\":null}");

        drafterOver(model).draft(NOTHING_MISSING, BRIEF);

        assertThat(model.prompts.getLast()).contains("Group CFO", "C-Suite", "(required) Listed-company CFO",
                "Own the IPO", "Capital markets readiness",
                "T1 [technical, weight 60] Capital markets: IPO and debt raising",
                "T2 [technical, weight 40] Treasury", "B1 [behavioural, weight 100] Board presence",
                "technical carries 70%");
    }

    @Test
    @DisplayName("no competencies asks the model nothing, and a model that cannot be reached drafts nothing")
    void nothingIsDraftedWithoutCompetenciesOrAModel() {
        RecordingChatModel model = new RecordingChatModel("{\"scores\":[]}");
        AssessmentBrief noCompetencies = new AssessmentBrief("Group CFO", null, null, null, null, null, List.of(),
                null, List.of(), List.of(), new BriefCompetencies(List.of(), 50));

        assertThat(drafterOver(model).draft(NOTHING_MISSING, noCompetencies)).isEmpty();
        assertThat(model.prompts).isEmpty();

        CandidateScorecardDrafter unreachable = drafterOver(new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new IllegalStateException("Failed to get application default credentials");
            }
        });
        assertThat(unreachable.draft(NOTHING_MISSING, BRIEF)).isEmpty();
    }

    private static CandidateScorecardDrafter drafterOver(ChatModel model) {
        return new CandidateScorecardDrafter(TestLlmCallPolicy.promptsOver(model));
    }
}
