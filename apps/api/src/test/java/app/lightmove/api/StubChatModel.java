package app.lightmove.api;

import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;

/**
 * A {@link ChatModel} that answers every prompt with a fixed reply, so the whole application
 * context still loads under the {@code test} profile — see {@code application-test.yml}, which
 * turns off the real Google GenAI auto-configuration so no test needs GCP credentials.
 * {@link app.lightmove.api.core.llm.config.ChatClientConfig} still needs a {@code ChatModel} to
 * build its {@code ChatClient} bean; this is that bean.
 */
public class StubChatModel implements ChatModel {

    private static final String REPLY = "stubbed response";

    @Override
    public ChatResponse call(Prompt prompt) {
        return chunk(REPLY);
    }

    /**
     * The assistant runner streams, and {@code ChatModel}'s default {@code stream} throws
     * {@code UnsupportedOperationException} — so this has to be implemented, not inherited. Two
     * chunks rather than one, so a turn's text genuinely arrives in pieces and is reassembled the way
     * a real provider's would be.
     */
    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(chunk("stubbed "), chunk("response"));
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
