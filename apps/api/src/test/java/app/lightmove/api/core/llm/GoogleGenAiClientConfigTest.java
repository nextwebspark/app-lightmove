package app.lightmove.api.core.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.LlmRateLimitSettings;
import app.lightmove.api.core.config.LlmSettings;
import java.util.List;
import app.lightmove.api.core.llm.config.GoogleGenAiClientConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The client bean that exists to carry a request timeout Spring AI's own bean has no way to set.
 *
 * <p>It reproduces the Vertex path only, so what is worth pinning is that it refuses the
 * configurations it does <i>not</i> reproduce rather than quietly building the wrong client.
 */
class GoogleGenAiClientConfigTest {

    private final GoogleGenAiClientConfig config = new GoogleGenAiClientConfig();

    @Test
    @DisplayName("refuses an api-key rather than silently building a Vertex client instead")
    void refusesAnApiKey() {
        // An api-key means the Gemini Developer API. Building Vertex anyway would authenticate as the
        // service account and bill a project the operator did not name.
        assertThatThrownBy(() -> config.googleGenAiClient("a-project", "us-central1", "a-key", properties(20_000)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("api-key");
    }

    @Test
    @DisplayName("refuses an incomplete Vertex configuration")
    void refusesAHalfConfiguredVertex() {
        assertThatThrownBy(() -> config.googleGenAiClient("a-project", "", "", properties(20_000)))
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

    @Test
    @DisplayName("the settings carry the timeout the client is built with")
    void carriesTheTimeout() {
        assertThat(properties(15_000).llm().requestTimeoutMs()).isEqualTo(15_000);
    }

    private static LightMoveProperties properties(int timeoutMs) {
        return new LightMoveProperties(null, null, null, null, null,
                new LlmSettings(new LlmRateLimitSettings(true, 10, 20), timeoutMs, 1,
                        List.of("ignore previous instructions")));
    }
}
