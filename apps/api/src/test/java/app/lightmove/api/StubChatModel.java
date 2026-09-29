package app.lightmove.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
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

    private volatile Prompt lastPrompt;
    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    private volatile String reply = REPLY;
    private final Map<String, String> repliesBySystemText = new ConcurrentHashMap<>();

    @Override
    public ChatResponse call(Prompt prompt) {
        lastPrompt = prompt;
        prompts.add(prompt);
        String system = prompt.getSystemMessage().getText();
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

    /** Answers every prompt with {@code text} until {@link #reset}; the context is shared, so reset it. */
    public void answerWith(String text) {
        reply = text;
    }

    public void reset() {
        reply = REPLY;
        repliesBySystemText.clear();
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

    private static ChatResponse chunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        public StubChatModel stubChatModel() {
            return new StubChatModel();
        }
    }
}
