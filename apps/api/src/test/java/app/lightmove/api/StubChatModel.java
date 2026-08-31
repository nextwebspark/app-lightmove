package app.lightmove.api;

import java.util.List;
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

    @Override
    public ChatResponse call(Prompt prompt) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("stubbed response"))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        public StubChatModel stubChatModel() {
            return new StubChatModel();
        }
    }
}
