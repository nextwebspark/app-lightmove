package app.lightmove.api.core.llm.config;

import app.lightmove.api.core.llm.service.DisabledChatModel;
import app.lightmove.api.core.llm.service.DisabledEmbeddingModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Boots with no model, for a runtime with no GCP credentials (the e2e matrix's boot died on ADC for four weeks). Pairs
 * with {@code chat: none} and {@code embedding.text: none}, restoring the two beans every AI feature needs; its own
 * switch, since the test profile sets {@code chat: none} with stubs of its own.
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
