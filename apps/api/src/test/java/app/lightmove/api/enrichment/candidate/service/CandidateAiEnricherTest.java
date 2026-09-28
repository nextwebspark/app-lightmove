package app.lightmove.api.enrichment.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.TestLlmCallPolicy;
import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.candidate.model.CandidateAiEnrichment;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.InferredBackground;
import app.lightmove.api.common.constant.CriterionMode;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.position.dto.AssessmentDto;
import app.lightmove.api.position.dto.CompetencyDto;
import app.lightmove.api.position.dto.CriterionResponse;
import app.lightmove.api.position.dto.PositionDetailsDto;
import app.lightmove.api.position.dto.PositionResponse;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

class CandidateAiEnricherTest {

    private static final Set<BackgroundField> ALL_MISSING = EnumSet.allOf(BackgroundField.class);

    private static final PositionResponse BRIEF = new PositionResponse(
            new PositionDetailsDto("Group CFO", null, null, null, null, null, List.of(), null, Map.of()),
            null, null, null,
            new AssessmentDto(List.of(new CriterionResponse("Listed-company CFO", CriterionMode.REQUIRED, null)),
                    List.of(new CompetencyDto("Capital markets", "IPO and debt raising", 60, null)),
                    List.of(new CompetencyDto("Board presence", null, 40, null)), 60),
            null, null);

    private static final String FULL_ANSWER = """
            {"gender":"Female","yearsExperience":14,"seniority":"N-1",
             "summary":" A proven finance leader. ",
             "technical":{"score":8,"positives":["a","b","c","d","e","f"],"negatives":["g"]},
             "behavioural":{"score":6,"positives":["h"],"negatives":[" ", "i"]}}""";

    @Test
    @DisplayName("an answer maps to canonical background and clamped panels, asked as JSON without web search")
    void anAnswerIsMapped() {
        RecordingChatModel model = new RecordingChatModel(FULL_ANSWER);

        CandidateAiEnrichment enriched = enricherOver(model).enrich(dossier(ALL_MISSING), BRIEF).orElseThrow();

        assertThat(enriched.background()).isEqualTo(new InferredBackground(Gender.FEMALE, 14, Seniority.N_MINUS_1));
        assertThat(enriched.assessment().summary()).isEqualTo("A proven finance leader.");
        assertThat(enriched.assessment().technical().score()).isEqualTo(8);
        assertThat(enriched.assessment().technical().positives()).containsExactly("a", "b", "c", "d", "e");
        assertThat(enriched.assessment().behavioural().negatives()).containsExactly("i");
        assertThat(enriched.assessment().assessedAt()).isNotNull();
        GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) model.options.getLast();
        assertThat(options.getGoogleSearchRetrieval()).isNotEqualTo(true);
        assertThat(options.getResponseMimeType()).isEqualTo("application/json");
        assertThat(options.getTemperature()).isEqualTo(0.0);
        assertThat(model.prompts.getLast()).contains("Capital markets (weight 60)", "Board presence (weight 40)",
                "(required) Listed-company CFO", "Group CFO");
    }

    @Test
    @DisplayName("an out-of-range score or level is dropped, and only missing background is answered")
    void outOfRangeAndPresentFieldsAreDropped() {
        CandidateAiEnrichment enriched = enricherOver(new RecordingChatModel("""
                {"gender":"male","yearsExperience":75,"seniority":"Chief",
                 "technical":{"score":11},"behavioural":null}"""))
                .enrich(dossier(EnumSet.of(BackgroundField.SENIORITY, BackgroundField.YEARS_EXPERIENCE)), BRIEF)
                .orElseThrow();

        assertThat(enriched.background()).isEqualTo(InferredBackground.NONE);
        assertThat(enriched.assessment().technical().score()).isNull();
        assertThat(enriched.assessment().behavioural().score()).isNull();
    }

    @Test
    @DisplayName("nationality is never asked of the assessment call — the classifier answers it")
    void nationalityIsNotAsked() {
        RecordingChatModel model = new RecordingChatModel(FULL_ANSWER);

        enricherOver(model).enrich(dossier(EnumSet.of(BackgroundField.NATIONALITY)), BRIEF).orElseThrow();

        assertThat(model.prompts.getLast()).contains("Background fields still to propose: none")
                .doesNotContain("Western expat");
    }

    @Test
    @DisplayName("a model that cannot be reached stores nothing")
    void aFailedCallStoresNothing() {
        CandidateAiEnricher enricher = enricherOver(new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new IllegalStateException("Failed to get application default credentials");
            }
        });

        assertThat(enricher.enrich(dossier(ALL_MISSING), BRIEF)).isEmpty();
    }

    private static CandidateDossier dossier(Set<BackgroundField> missing) {
        return new CandidateDossier("Sample Person", "Group CFO", "Al Rawabi Dairy", "Dubai",
                "United Arab Emirates", "https://www.linkedin.com/in/sample-profile", "Finance leader.",
                List.of(new CandidateCareerEntry("Al Rawabi Dairy", "Group CFO", "2011 – Present", null)),
                List.of(), List.of("Treasury"), List.of("English"), missing);
    }

    private static CandidateAiEnricher enricherOver(ChatModel model) {
        return new CandidateAiEnricher(TestLlmCallPolicy.promptsOver(model));
    }
}
