package app.lightmove.api.core.llm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.LlmRateLimitSettings;
import app.lightmove.api.core.config.LlmSettings;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The client bean that exists to carry a request timeout Spring AI's own bean has no way to set.
 *
 * <p>It reproduces the Vertex path only, so what is worth pinning is that the timeout really reaches
 * the provider's options, and that every configuration it does <i>not</i> reproduce is refused rather
 * than quietly ignored.
 */
class GoogleGenAiClientConfigTest {

    private final GoogleGenAiClientConfig config = new GoogleGenAiClientConfig();

    @Test
    @DisplayName("the configured timeout reaches the provider's own HttpOptions")
    void carriesTheTimeoutIntoHttpOptions() {
        // The one line here that a wrong unit or a dropped .timeout(...) would break.
        assertThat(GoogleGenAiClientConfig.httpOptionsWith(15_000).timeout()).contains(15_000);
    }

    @Test
    @DisplayName("refuses an api-key rather than silently building a Vertex client instead")
    void refusesAnApiKey() {
        // An api-key means the Gemini Developer API. Building Vertex anyway would authenticate as the
        // service account and bill a project the operator did not name.
        assertThatThrownBy(() -> build("a-project", "us-central1", "a-key", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("api-key");
    }

    @Test
    @DisplayName("refuses a credentials-uri rather than authenticating as whoever the runtime is")
    void refusesACredentialsUri() {
        // The replaced bean loads credentials from this. Ignoring it silently would leave the client
        // on Application Default Credentials with nothing saying the configured identity was dropped.
        assertThatThrownBy(() -> build("a-project", "us-central1", "", "file:/creds.json", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("credentials-uri");
    }

    @Test
    @DisplayName("refuses the vertex-ai flag rather than ignoring an operator who turned it off")
    void refusesTheVertexAiFlag() {
        assertThatThrownBy(() -> build("a-project", "us-central1", "", "", "false"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vertex-ai");
    }

    @Test
    @DisplayName("refuses an incomplete Vertex configuration")
    void refusesAHalfConfiguredVertex() {
        assertThatThrownBy(() -> build("a-project", "", "", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("location");
    }

    @Test
    @DisplayName("a non-positive timeout is refused at binding, not at the first call")
    void refusesANonPositiveTimeout() {
        assertThatThrownBy(() -> properties(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request-timeout-ms");
    }

    private void build(String projectId, String location, String apiKey, String credentialsUri, String vertexAi) {
        config.googleGenAiClient(projectId, location, apiKey, credentialsUri, vertexAi, properties(20_000));
    }

    private static LightMoveProperties properties(int timeoutMs) {
        return new LightMoveProperties(null, null, null, null, null,
                new LlmSettings(new LlmRateLimitSettings(true, 10, 20), timeoutMs, 1,
                        List.of("ignore previous instructions")),
                null, null, null);
    }
}
