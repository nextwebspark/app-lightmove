package app.lightmove.api.enrichment.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.TestLlmCallPolicy;
import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.CandidateEducationEntry;
import app.lightmove.api.candidate.model.NationalityReading;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

class CandidateNationalityClassifierTest {

    private static final CandidateDossier DOSSIER = new CandidateDossier("Sample Person", "Group CFO",
            "Al Rawabi Dairy", "Dubai", "United Arab Emirates", "https://www.linkedin.com/in/sample-profile",
            "Finance leader.",
            List.of(new CandidateCareerEntry("Al Rawabi Dairy", "Group CFO", "2015 – Present", "Dubai"),
                    new CandidateCareerEntry("Deloitte", "Audit Associate", "2004 – 2008", "Cairo, Egypt")),
            List.of(new CandidateEducationEntry("Cairo University", "BCom", "2000 – 2004")),
            List.of(), List.of("Arabic", "English"), EnumSet.of(BackgroundField.NATIONALITY));

    @Test
    @DisplayName("a reading is kept in our spelling, asked at temperature 0 with the career oldest first")
    void aReadingIsMapped() {
        RecordingChatModel model = new RecordingChatModel("""
                {"category":"arab expat, NON-GCC","confidence":"High",
                 "evidence_for":["BCom, Cairo University","first job in Cairo"," ","a","b","c","d"],
                 "evidence_against":[],"rule_applied":"none"}""");

        NationalityReading reading = classifierOver(model).classify(DOSSIER).orElseThrow();

        assertThat(reading.category()).isEqualTo("Arab expat, non-GCC");
        assertThat(reading.confidence()).isEqualTo("high");
        assertThat(reading.isDecisive()).isTrue();
        assertThat(reading.evidenceFor())
                .containsExactly("BCom, Cairo University", "first job in Cairo", "a", "b", "c");
        assertThat(reading.rule()).isEqualTo("none");
        assertThat(reading.readAt()).isNotNull();
        GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) model.options.getLast();
        assertThat(options.getTemperature()).isEqualTo(0.0);
        String prompt = model.prompts.getLast();
        assertThat(prompt.indexOf("Audit Associate, Deloitte, Cairo, Egypt"))
                .isNotNegative()
                .isLessThan(prompt.indexOf("Group CFO, Al Rawabi Dairy, Dubai"));
    }

    @Test
    @DisplayName("a category no group carries is Unknown, and a nonsense confidence or rule is the weakest")
    void anUnknownLabelIsUnknown() {
        NationalityReading reading = classifierOver(new RecordingChatModel("""
                {"category":"Egyptian","confidence":"low","rule_applied":"Z"}"""))
                .classify(DOSSIER).orElseThrow();

        assertThat(reading.category()).isEqualTo(NationalityReading.UNKNOWN);
        assertThat(reading.confidence()).isEqualTo("low");
        assertThat(reading.rule()).isEqualTo("none");
        assertThat(reading.isDecisive()).isFalse();
    }

    @Test
    @DisplayName("the rubric's Subcontinent is stored as South Asian, and medium never decides")
    void subcontinentIsSouthAsian() {
        NationalityReading reading = classifierOver(new RecordingChatModel("""
                {"category":"Subcontinent","confidence":"medium","rule_applied":"B"}"""))
                .classify(DOSSIER).orElseThrow();

        assertThat(reading.category()).isEqualTo("South Asian");
        assertThat(reading.rule()).isEqualTo("B");
        assertThat(reading.isDecisive()).isFalse();
    }

    @Test
    @DisplayName("a model that cannot be reached answers nothing")
    void aFailedCallAnswersNothing() {
        CandidateNationalityClassifier classifier = classifierOver(new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new IllegalStateException("Failed to get application default credentials");
            }
        });

        assertThat(classifier.classify(DOSSIER)).isEmpty();
    }

    private static CandidateNationalityClassifier classifierOver(ChatModel model) {
        return new CandidateNationalityClassifier(TestLlmCallPolicy.promptsOver(model));
    }
}
