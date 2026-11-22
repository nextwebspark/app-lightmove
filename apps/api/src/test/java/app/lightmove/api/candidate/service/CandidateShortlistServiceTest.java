package app.lightmove.api.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.TestLlmCallPolicy;
import app.lightmove.api.core.llm.service.PromptCalls;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

/** No GCP credentials or network calls — a hand-rolled {@link ChatModel} stands in for Gemini. */
class CandidateShortlistServiceTest {

    @Test
    void sendsBothFieldsAndReturnsTheModelsVerdict() {
        RecordingChatModel chatModel = new RecordingChatModel("SHORTLIST — strong sector match.");
        CandidateShortlistService service =
                new CandidateShortlistService(ChatClient.builder(chatModel).build(), PromptCalls.bundledOnly(),
                        TestLlmCallPolicy.asShipped());

        String verdict = service.shortlist(
                "Looking for a CFO in fintech.", "10 years as CFO at a fintech scale-up.");

        assertThat(verdict).isEqualTo("SHORTLIST — strong sector match.");

        String userText = chatModel.lastPrompt.getInstructions().stream()
                .filter(message -> message.getMessageType() == MessageType.USER)
                .map(Message::getText)
                .findFirst()
                .orElseThrow();
        assertThat(userText).contains("Looking for a CFO in fintech.");
        assertThat(userText).contains("10 years as CFO at a fintech scale-up.");
    }

    @Test
    @DisplayName("names only its prompt, so the configured model, temperature and ceiling still apply")
    void inheritsTheConfiguredOptions() {
        RecordingChatModel chatModel = new RecordingChatModel("SHORTLIST");
        new CandidateShortlistService(ChatClient.builder(chatModel).build(), PromptCalls.bundledOnly(),
                TestLlmCallPolicy.asShipped()).shortlist("a brief", "a profile");

        assertThat(chatModel.lastPrompt.getOptions()).isInstanceOfSatisfying(GoogleGenAiChatOptions.class, options -> {
            assertThat(options.getModel()).isEqualTo("gemini-3.5-flash");
            assertThat(options.getTemperature()).isEqualTo(0.4);
            assertThat(options.getMaxOutputTokens()).isEqualTo(8192);
            assertThat(options.getThinkingBudget()).isNull();
            assertThat(options.getResponseMimeType()).isNull();
            assertThat(options.getLabels())
                    .containsEntry("app", "lightmove-api")
                    .containsEntry("prompt", "recruiter-shortlist");
        });
    }

    private static final class RecordingChatModel implements ChatModel {
        private final String reply;
        private Prompt lastPrompt;

        private RecordingChatModel(String reply) {
            this.reply = reply;
        }

        /** As application.yml configures the model: what a call inherits where it names nothing. */
        @Override
        public ChatOptions getOptions() {
            return GoogleGenAiChatOptions.builder()
                    .model("gemini-3.5-flash")
                    .temperature(0.4)
                    .maxOutputTokens(8192)
                    .labels(Map.of("app", "lightmove-api"))
                    .build();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.lastPrompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        }
    }
}
