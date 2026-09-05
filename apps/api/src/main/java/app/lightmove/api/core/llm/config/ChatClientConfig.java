package app.lightmove.api.core.llm.config;

import app.lightmove.api.core.llm.service.ChatCallLog;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one {@link ChatClient} the application talks to Gemini through, and what every call logs. No
 * system prompt here — that belongs at each feature's call site — and no guard either, which is
 * {@code LlmCallPolicy}, applied per prompt.
 *
 * <p>No default options either, which is the part worth knowing. Spring AI has two layers that look
 * alike and behave differently. The ChatModel's own options, configured under
 * {@code spring.ai.google.genai.chat}, are the base a call's {@code .options(...)} is merged onto
 * field by field, so a caller inherits everything it does not name. Options set on this bean go into
 * the same slot as that per-call call — {@code Builder.defaultOptions} and {@code .options} are one
 * setter, and it assigns — so the first call to name any option would drop all of them. Model,
 * temperature, answer ceiling and billing label therefore live in {@code application.yml}.
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder
                .defaultAdvisors(SimpleLoggerAdvisor.builder()
                        .requestToString(ChatCallLog::describeRequest)
                        .responseToString(ChatCallLog::describeResponse)
                        .build())
                .build();
    }
}
