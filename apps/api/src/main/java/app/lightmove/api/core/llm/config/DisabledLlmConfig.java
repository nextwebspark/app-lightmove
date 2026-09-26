package app.lightmove.api.core.llm.config;

import app.lightmove.api.core.llm.service.DisabledChatModel;
import app.lightmove.api.core.llm.service.DisabledEmbeddingModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Boots the application with no model behind it, for a runtime holding no GCP credentials — the e2e
 * matrix, whose boot died on Application Default Credentials for four weeks of nightly runs.
 *
 * <p>Pairs with {@code spring.ai.model.chat: none} and {@code embedding.text: none}, which take the
 * Google auto-configuration away; this puts back the two beans every AI feature is constructed with.
 * Its own switch rather than keying on {@code chat: none}, because the test profile sets that too and
 * supplies its own stubs.
 */
@Configuration
@ConditionalOnProperty(name = "lightmove.llm.enabled", havingValue = "false")
public class DisabledLlmConfig {

    @Bean
    public ChatModel disabledChatModel() {
        return new DisabledChatModel();
    }

    @Bean
    public EmbeddingModel disabledEmbeddingModel() {
        return new DisabledEmbeddingModel();
    }
}
