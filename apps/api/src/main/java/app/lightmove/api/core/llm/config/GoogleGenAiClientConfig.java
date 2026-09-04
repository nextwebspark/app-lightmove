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
                                    @Value("${spring.ai.google.genai.credentials-uri:}") String credentialsUri,
                                    @Value("${spring.ai.google.genai.vertex-ai:}") String vertexAiFlag,
                                    LightMoveProperties properties) {
        refuseUnhonoured("api-key", apiKey);
        // The replaced bean reads this and loads credentials from it. This one does not, and silence
        // would leave the client authenticating as whatever identity the runtime happens to carry.
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

    /**
     * The provider's own timeout option, extracted so it can be asserted on: {@code Client} exposes no
     * way to read its {@code HttpOptions} back, so building one proves nothing about what it carries.
     */
    static HttpOptions httpOptionsWith(int timeoutMs) {
        return HttpOptions.builder().timeout(timeoutMs).build();
    }

    /**
     * Refuses a property this bean does not read.
     *
     * <p>Every one of these changes how the replaced bean would have authenticated or connected, so
     * ignoring it silently is the failure worth refusing: the operator gets the behaviour they
     * configured against, with nothing saying otherwise.
     */
    private static void refuseUnhonoured(String property, String value) {
        if (StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "spring.ai.google.genai." + property + " is set, but GoogleGenAiClientConfig builds a "
                            + "Vertex client from project-id and location only, and would ignore it. "
                            + "Unset it, or drop this bean and lose the request timeout.");
        }
    }
}
