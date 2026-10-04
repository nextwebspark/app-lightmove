package app.lightmove.api.core.llm.config;

import app.lightmove.api.core.llm.service.ChatCallLog;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one {@link ChatClient} to Gemini, with logging only. <b>No default options:</b> they share the
 * slot a call's {@code .options(...)} assigns, so the first call naming any option would drop them all;
 * model, temperature, ceiling and billing label live in {@code application.yml} instead.
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
