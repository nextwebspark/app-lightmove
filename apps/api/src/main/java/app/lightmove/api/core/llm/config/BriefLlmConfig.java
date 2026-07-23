package app.lightmove.api.core.llm.config;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.llm.service.BriefLlmClient;
import app.lightmove.api.core.llm.service.LogBriefLlmClient;
import app.lightmove.api.core.llm.service.VertexAiBriefLlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Picks the {@link BriefLlmClient} adapter from config, mirroring {@code EmailSenderConfig}. */
@Configuration
@Slf4j
public class BriefLlmConfig {

    @Bean
    BriefLlmClient briefLlmClient(LightMoveProperties properties, ObjectMapper json) {
        LightMoveProperties.Llm config = properties.llm();

        if (!"vertex-ai".equalsIgnoreCase(config.provider())) {
            log.info("LLM provider is '{}' — brief documents will be stored but not extracted.",
                    config.provider());
            return new LogBriefLlmClient();
        }

        LightMoveProperties.Llm.VertexAi vertexAi = config.vertexAi();
        if (vertexAi == null || vertexAi.projectId() == null || vertexAi.projectId().isBlank()) {
            throw new IllegalStateException(
                    "lightmove.llm.provider=vertex-ai but no project is set (GCP_PROJECT_ID)");
        }

        log.info("LLM provider is Vertex AI, model {} in {}", vertexAi.model(), vertexAi.location());
        return new VertexAiBriefLlmClient(vertexAi.projectId(), vertexAi.location(), vertexAi.model(), json);
    }
}
