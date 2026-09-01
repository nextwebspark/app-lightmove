package app.lightmove.api.core.llm.config;

import app.lightmove.api.core.config.LightMoveProperties;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * The Google GenAI client, built here so it carries a request timeout.
 *
 * <p>Spring AI's {@code GoogleGenAiChatAutoConfiguration} declares this bean
 * {@code @ConditionalOnMissingBean} and gives it no timeout, and neither its connection properties nor
 * {@code GoogleGenAiChatOptions} expose one — so replacing the bean is the only seam the provider
 * offers. {@link HttpOptions#timeout} is that provider's own option; nothing here wraps or races the
 * call.
 *
 * <p>It reproduces only the <b>Vertex</b> path, which is the one this application runs
 * ({@code application.yml} sets project-id and location and deliberately sets no api-key). An api-key
 * means the Gemini Developer API, which this does not build — so it is refused loudly rather than
 * quietly served a Vertex client.
 */
@Configuration
// The test profile sets spring.ai.model.chat=none precisely so no test needs GCP credentials. Without
// this gate the bean would build a real client in every @SpringBootTest and resolve Application
// Default Credentials CI does not have — the failure PR #148 hit with the embedding connection.
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai", matchIfMissing = true)
public class GoogleGenAiClientConfig {

    @Bean
    public Client googleGenAiClient(@Value("${spring.ai.google.genai.project-id:}") String projectId,
                                    @Value("${spring.ai.google.genai.location:}") String location,
                                    @Value("${spring.ai.google.genai.api-key:}") String apiKey,
                                    LightMoveProperties properties) {
        if (StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(
                    "spring.ai.google.genai.api-key is set, but GoogleGenAiClientConfig builds a Vertex "
                            + "client only. Remove the key, or drop this bean and lose the request timeout.");
        }
        if (!StringUtils.hasText(projectId) || !StringUtils.hasText(location)) {
            throw new IllegalStateException(
                    "Vertex AI needs spring.ai.google.genai.project-id and .location");
        }

        return Client.builder()
                .vertexAI(true)
                .project(projectId)
                .location(location)
                .httpOptions(HttpOptions.builder()
                        .timeout(properties.llm().requestTimeoutMs())
                        .build())
                .build();
    }
}
