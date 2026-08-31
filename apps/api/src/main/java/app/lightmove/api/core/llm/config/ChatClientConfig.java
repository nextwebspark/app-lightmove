package app.lightmove.api.core.llm.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one {@link ChatClient} the application talks to Gemini through. The model bean itself
 * ({@code GoogleGenAiChatModel}) is auto-configured from {@code spring.ai.google.genai.*} — this
 * only fixes the call-level defaults every feature should share: which model, how creative it is
 * allowed to be, and that every call is logged. No system prompt lives here — that is specific to
 * whichever feature is calling, and belongs at that feature's own call site instead.
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
                        // Metadata only, never prompt/response content: a job brief or candidate
                        // profile is client and candidate PII, and must never land in the app log.
                        .requestToString(ChatClientConfig::describeRequest)
                        .responseToString(ChatClientConfig::describeResponse)
                        .build())
                .build();
    }

    private static String describeRequest(ChatClientRequest request) {
        ChatOptions options = request == null ? null : request.prompt().getOptions();
        return "chat request model=" + (options == null ? "default" : options.getModel());
    }

    private static String describeResponse(ChatResponse response) {
        if (response == null) {
            return "chat response: null";
        }
        var metadata = response.getMetadata();
        var usage = metadata.getUsage();
        return "chat response model=%s promptTokens=%s completionTokens=%s".formatted(
                metadata.getModel(), usage.getPromptTokens(), usage.getCompletionTokens());
    }
}
