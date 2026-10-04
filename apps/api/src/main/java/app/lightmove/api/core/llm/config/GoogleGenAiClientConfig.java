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
 * The Google GenAI client, replaced so it carries a request timeout — the auto-configured one has none
 * and exposes no option for it. Builds only the Vertex path; properties it would ignore are refused.
 */
@Configuration
// Without this gate every @SpringBootTest would resolve Application Default Credentials CI lacks (PR #148).
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai", matchIfMissing = true)
public class GoogleGenAiClientConfig {

    @Bean
    public Client googleGenAiClient(@Value("${spring.ai.google.genai.project-id:}") String projectId,
                                    @Value("${spring.ai.google.genai.location:}") String location,
                                    @Value("${spring.ai.google.genai.api-key:}") String apiKey,
                                    @Value("${spring.ai.google.genai.credentials-uri:}") String credentialsUri,
                                    @Value("${spring.ai.google.genai.vertex-ai:}") String vertexAiFlag,
                                    LightMoveProperties properties) {
        refuseUnhonoured("api-key", apiKey);
        // Ignored silently, the client would authenticate as whatever identity the runtime carries.
        refuseUnhonoured("credentials-uri", credentialsUri);
        refuseUnhonoured("vertex-ai", vertexAiFlag);
        if (!StringUtils.hasText(projectId) || !StringUtils.hasText(location)) {
            throw new IllegalStateException(
                    "Vertex AI needs spring.ai.google.genai.project-id and .location");
        }

        return Client.builder()
                .vertexAI(true)
                .project(projectId)
                .location(location)
                .httpOptions(httpOptionsWith(properties.llm().requestTimeoutMs()))
                .build();
    }

    /** Extracted for tests: {@code Client} cannot report its {@code HttpOptions} back. */
    static HttpOptions httpOptionsWith(int timeoutMs) {
        return HttpOptions.builder().timeout(timeoutMs).build();
    }

    private static void refuseUnhonoured(String property, String value) {
        if (StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "spring.ai.google.genai." + property + " is set, but GoogleGenAiClientConfig builds a "
                            + "Vertex client from project-id and location only, and would ignore it. "
                            + "Unset it, or drop this bean and lose the request timeout.");
        }
    }
}
