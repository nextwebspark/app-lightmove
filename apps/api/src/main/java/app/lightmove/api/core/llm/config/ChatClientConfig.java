package app.lightmove.api.core.llm.config;

import app.lightmove.api.core.llm.service.ChatCallLog;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one {@link ChatClient} the application talks to Gemini through: which model, how creative it may
 * be, and what every call logs. No system prompt here — that belongs at each feature's call site, and
 * no guard either — that is {@code LlmCallPolicy}, applied per prompt.
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder,
                                 @Value("${spring.ai.google.genai.chat.model}") String model,
                                 @Value("${spring.ai.google.genai.chat.temperature}") double temperature) {
        return chatClientBuilder
                .defaultOptions(ChatOptions.builder()
                        .model(model)
                        .temperature(temperature))
                .defaultAdvisors(SimpleLoggerAdvisor.builder()
                        .requestToString(ChatCallLog::describeRequest)
                        .responseToString(ChatCallLog::describeResponse)
                        .build())
                .build();
    }
}
