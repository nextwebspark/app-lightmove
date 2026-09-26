package app.lightmove.api.enrichment.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.TestLlmCallPolicy;
import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.candidate.model.AssessmentSourceLink;
import app.lightmove.api.candidate.model.CandidateAiEnrichment;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.InferredBackground;
import app.lightmove.api.common.constant.CriterionMode;
import app.lightmove.api.position.dto.AssessmentDto;
import app.lightmove.api.position.dto.CompetencyDto;
import app.lightmove.api.position.dto.CriterionResponse;
import app.lightmove.api.position.dto.PositionDetailsDto;
import app.lightmove.api.position.dto.PositionResponse;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
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
            {"nationality":"western EXPAT","gender":"Female","yearsExperience":14,
             "summary":" A proven finance leader. ",
             "technical":{"score":8,"positives":["a","b","c","d","e","f"],"negatives":["g"]},
             "behavioural":{"score":6,"positives":["h"],"negatives":[" ", "i"]},
             "sources":[{"url":"https://news.example.com/a","title":"A"},
                        {"url":"https://news.example.com/a","title":"A again"},
                        {"url":"https://uk.linkedin.com/in/someone","title":"LinkedIn"},
                        {"url":"javascript:alert(1)","title":"Bad"},
                        {"url":"not a url","title":"Bad"}]}""";

    @Test
    @DisplayName("a grounded answer maps to canonical background, clamped panels and clean sources")
    void aGroundedAnswerIsMapped() {
        RecordingChatModel model = new RecordingChatModel(FULL_ANSWER);

        CandidateAiEnrichment enriched = enricherOver(model).enrich(dossier(ALL_MISSING), BRIEF).orElseThrow();

        assertThat(enriched.background()).isEqualTo(new InferredBackground("Western expat", Gender.FEMALE, 14));
        assertThat(enriched.assessment().summary()).isEqualTo("A proven finance leader.");
        assertThat(enriched.assessment().technical().score()).isEqualTo(8);
        assertThat(enriched.assessment().technical().positives()).containsExactly("a", "b", "c", "d", "e");
        assertThat(enriched.assessment().behavioural().negatives()).containsExactly("i");
        assertThat(enriched.assessment().sources())
                .containsExactly(new AssessmentSourceLink("https://news.example.com/a", "A"));
        assertThat(enriched.assessment().assessedAt()).isNotNull();
        assertThat(((GoogleGenAiChatOptions) model.options.getLast()).getGoogleSearchRetrieval()).isTrue();
        assertThat(model.prompts.getLast()).contains("Capital markets (weight 60)", "Board presence (weight 40)",
                "(required) Listed-company CFO", "Group CFO");
    }

    @Test
    @DisplayName("an out-of-range score is dropped, and only missing background is answered")
    void outOfRangeAndPresentFieldsAreDropped() {
        CandidateAiEnrichment enriched = enricherOver(new RecordingChatModel("""
                {"nationality":"Saudi","gender":"male","yearsExperience":75,
                 "technical":{"score":11},"behavioural":null,"sources":[]}"""))
                .enrich(dossier(EnumSet.of(BackgroundField.NATIONALITY, BackgroundField.YEARS_EXPERIENCE)), BRIEF)
                .orElseThrow();

        assertThat(enriched.background()).isEqualTo(new InferredBackground("Saudi", null, null));
        assertThat(enriched.assessment().technical().score()).isNull();
        assertThat(enriched.assessment().behavioural().score()).isNull();
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
                List.of(new CandidateCareerEntry("Al Rawabi Dairy", "Group CFO", "2011 – Present")),
                List.of(), List.of("Treasury"), List.of("English"), missing);
    }

    private static CandidateAiEnricher enricherOver(ChatModel model) {
        return new CandidateAiEnricher(TestLlmCallPolicy.promptsOver(model));
    }

    /** Answers as {@code GoogleGenAiChatModel} would, so the call's own Google options are merged onto these. */
    private static final class RecordingChatModel implements ChatModel {

        private final String reply;
        private final List<String> prompts = new ArrayList<>();
        private final List<ChatOptions> options = new ArrayList<>();

        private RecordingChatModel(String reply) {
            this.reply = reply;
        }

        @Override
        public ChatOptions getOptions() {
            return GoogleGenAiChatOptions.builder().model("gemini-2.5-flash").build();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt.getInstructions().stream().map(message -> message.getText())
                    .reduce("", (all, text) -> all + text + "\n"));
            options.add(prompt.getOptions());
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        }
    }
}
