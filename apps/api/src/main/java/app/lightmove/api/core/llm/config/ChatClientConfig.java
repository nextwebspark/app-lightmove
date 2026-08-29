package app.lightmove.api.core.llm.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * The one {@link ChatClient} the application talks to Gemini through. The model bean itself
 * ({@code GoogleGenAiChatModel}) is auto-configured from {@code spring.ai.google.genai.*} — this
 * only fixes the call-level defaults every feature should share: which model, how creative it is
 * allowed to be, the system prompt, and that every call is logged.
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder,
                                 @Value("classpath:prompts/recruiter-shortlist-system.st") Resource systemPrompt) {
        return chatClientBuilder
                .defaultOptions(ChatOptions.builder()
                        .model("gemini-2.5-flash")
                        .temperature(0.8))
                .defaultAdvisors(SimpleLoggerAdvisor.builder().build())
                .defaultSystem(systemPrompt)
                .build();
    }
}
