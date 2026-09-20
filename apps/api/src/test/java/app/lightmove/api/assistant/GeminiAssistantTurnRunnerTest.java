package app.lightmove.api.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.assistant.model.AssistantAnswer;
import app.lightmove.api.assistant.model.AssistantExchange;
import app.lightmove.api.assistant.model.AssistantTurnPrompt;
import app.lightmove.api.assistant.service.GeminiAssistantTurnRunner;
import app.lightmove.api.core.config.AssistantSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import java.util.ArrayList;
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

/**
 * What one turn actually sends, and what it makes of what comes back.
 *
 * <p>The model assertion is the load-bearing one: the assistant pins a Pro-tier model per call
 * because the shared client's default is the Flash-tier one the extraction prompts use, and
 * {@code ChatClientConfig} explains why that can only happen per call.
 */
class GeminiAssistantTurnRunnerTest {

    @Test
    @DisplayName("pins its own model and thinking budget rather than taking the application's")
    void pinsItsOwnModel() {
        RecordingChatModel model = new RecordingChatModel("six companies");

        runnerWith(model, 12).run(new AssistantTurnPrompt("You are Uncava.", List.of(), "top IPPs?"));

        ChatOptions sent = model.lastOptions();
        assertThat(sent.getModel())
                .as("the shared client defaults to the Flash-tier extraction model")
                .isEqualTo("gemini-3.1-pro");
        assertThat(((GoogleGenAiChatOptions) sent).getThinkingBudget()).isEqualTo(2048);
        assertThat(sent.getTemperature()).isEqualTo(0.2);
    }

    @Test
    @DisplayName("labels the call so its spend is separable from every other prompt's")
    void labelsTheCall() {
        RecordingChatModel model = new RecordingChatModel("ok");

        runnerWith(model, 12).run(new AssistantTurnPrompt("sys", List.of(), "q"));

        assertThat(((GoogleGenAiChatOptions) model.lastOptions()).getLabels())
                .containsEntry("prompt", "assistant-turn");
    }

    @Test
    @DisplayName("sends the thread as alternating turns, oldest first, then the new question")
    void sendsTheThreadInOrder() {
        RecordingChatModel model = new RecordingChatModel("ok");

        runnerWith(model, 12).run(new AssistantTurnPrompt("sys",
                List.of(new AssistantExchange("who runs TAQA?", "Ahmed Ali")), "and Masdar?"));

        assertThat(model.lastConversation()).containsExactly(
                "USER:who runs TAQA?", "ASSISTANT:Ahmed Ali", "USER:and Masdar?");
    }

    @Test
    @DisplayName("drops the oldest exchanges when the thread outgrows the window")
    void windowsTheHistoryFromTheFront() {
        RecordingChatModel model = new RecordingChatModel("ok");
        List<AssistantExchange> longThread = List.of(
                new AssistantExchange("first", "1"),
                new AssistantExchange("second", "2"),
                new AssistantExchange("third", "3"));

        runnerWith(model, 2).run(new AssistantTurnPrompt("sys", longThread, "fourth"));

        assertThat(model.lastConversation())
                .as("the window keeps the most recent exchanges, not the first ones")
                .containsExactly("USER:second", "ASSISTANT:2", "USER:third", "ASSISTANT:3", "USER:fourth");
    }

    @Test
    @DisplayName("a turn that failed contributes its question and no invented answer")
    void keepsAnUnansweredQuestion() {
        // Dropping the pair would hide from the model that it was already asked — and it is the most
        // likely thing to be asked again, because nobody got an answer the first time.
        RecordingChatModel model = new RecordingChatModel("ok");

        runnerWith(model, 12).run(new AssistantTurnPrompt("sys",
                List.of(new AssistantExchange("what failed?", null)), "again?"));

        assertThat(model.lastConversation()).containsExactly("USER:what failed?", "USER:again?");
    }

    @Test
    @DisplayName("reports no token counts rather than zero when the provider reported none")
    void doesNotInventTokenCounts() {
        // StubChatModel answers the integration suite with empty metadata, and a zero here would read
        // as a call that cost nothing rather than one nobody measured.
        RecordingChatModel model = new RecordingChatModel("ok");

        AssistantAnswer answer = runnerWith(model, 12)
                .run(new AssistantTurnPrompt("sys", List.of(), "q"));

        assertThat(answer.text()).isEqualTo("ok");
        assertThat(answer.inputTokens()).isNull();
        assertThat(answer.outputTokens()).isNull();
    }

    private static GeminiAssistantTurnRunner runnerWith(ChatModel model, int historyWindow) {
        LightMoveProperties properties = new LightMoveProperties(null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                new AssistantSettings("gemini-3.1-pro", 0.2, 2048, historyWindow));
        return new GeminiAssistantTurnRunner(ChatClient.builder(model).build(), properties);
    }

    /** As {@code ColumnMappingProposerTest} does it, but recording roles as well as text. */
    private static final class RecordingChatModel implements ChatModel {

        private final String reply;
        private final List<List<String>> conversations = new ArrayList<>();
        private final List<ChatOptions> options = new ArrayList<>();

        private RecordingChatModel(String reply) {
            this.reply = reply;
        }

        /** What {@code application.yml} configures on the model itself, which a call's options merge onto. */
        @Override
        public ChatOptions getOptions() {
            return GoogleGenAiChatOptions.builder()
                    .model("gemini-2.5-flash")
                    .temperature(0.8)
                    .labels(Map.of("app", "lightmove-api"))
                    .build();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            List<String> conversation = new ArrayList<>();
            for (Message message : prompt.getInstructions()) {
                if (message.getMessageType() != MessageType.SYSTEM) {
                    conversation.add(message.getMessageType() + ":" + message.getText());
                }
            }
            conversations.add(conversation);
            options.add(prompt.getOptions());
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        }

        List<String> lastConversation() {
            return conversations.getLast();
        }

        ChatOptions lastOptions() {
            return options.getLast();
        }
    }
}
