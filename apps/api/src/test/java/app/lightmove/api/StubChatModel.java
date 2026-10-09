package app.lightmove.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * A {@link ChatModel} that answers every prompt with a fixed reply, so the whole application
 * context still loads under the {@code test} profile — see {@code application-test.yml}, which
 * turns off the real Google GenAI auto-configuration so no test needs GCP credentials.
 * {@link app.lightmove.api.core.llm.config.ChatClientConfig} still needs a {@code ChatModel} to
 * build its {@code ChatClient} bean; this is that bean.
 */
public class StubChatModel implements ChatModel {

    private static final String REPLY = "stubbed response";

    /** What every answer reports it took, so a caller summing a call's tokens has something to sum. */
    public static final int PROMPT_TOKENS = 120;
    public static final int COMPLETION_TOKENS = 30;

    private volatile Prompt lastPrompt;
    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    private volatile String reply = REPLY;
    private final Map<String, String> repliesBySystemText = new ConcurrentHashMap<>();
    private final Map<String, AssistantMessage.ToolCall> toolCallsBySystemText = new ConcurrentHashMap<>();

    /**
     * ChatClient builds every call's options from these, and only tool-calling options carry a call's tools —
     * so ChatClient's own tool loop runs a tool this stub asks for.
     */
    @Override
    public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder().build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        lastPrompt = prompt;
        prompts.add(prompt);
        String system = prompt.getSystemMessage().getText();
        if (!answersToolResults(prompt)) {
            AssistantMessage.ToolCall toolCall = toolCallsBySystemText.entrySet().stream()
                    .filter(marked -> system != null && system.contains(marked.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
            if (toolCall != null) {
                return new ChatResponse(List.of(new Generation(
                        AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build())));
            }
        }
        return chunk(repliesBySystemText.entrySet().stream()
                .filter(marked -> system != null && system.contains(marked.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(reply));
    }

    /** Answers a prompt whose system text contains {@code marker} with {@code text}, ahead of {@link #answerWith}. */
    public void answerWhenSystemContains(String marker, String text) {
        repliesBySystemText.put(marker, text);
    }

    /**
     * Calls {@code toolName} with {@code arguments} for a prompt whose system text contains {@code marker},
     * then answers as usual once the tool's result comes back — so the real tool runs, with its context.
     */
    public void callToolWhenSystemContains(String marker, String toolName, String arguments) {
        toolCallsBySystemText.put(marker, new AssistantMessage.ToolCall("stub-" + toolName, "function", toolName,
                arguments));
    }

    /** Answers every prompt with {@code text} until {@link #reset}; the context is shared, so reset it. */
    public void answerWith(String text) {
        reply = text;
    }

    public void reset() {
        reply = REPLY;
        repliesBySystemText.clear();
        toolCallsBySystemText.clear();
        lastPrompt = null;
        prompts.clear();
    }

    /** The last prompt sent, so a test can see what the model was told. */
    public Prompt lastPrompt() {
        return lastPrompt;
    }

    /** Every prompt sent since the last {@link #reset}, in order. */
    public List<Prompt> prompts() {
        return List.copyOf(prompts);
    }

    private static boolean answersToolResults(Prompt prompt) {
        List<Message> instructions = prompt.getInstructions();
        return !instructions.isEmpty() && instructions.getLast() instanceof ToolResponseMessage;
    }

    private static ChatResponse chunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(PROMPT_TOKENS, COMPLETION_TOKENS)).build());
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        public StubChatModel stubChatModel() {
            return new StubChatModel();
        }
    }
}
