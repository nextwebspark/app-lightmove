package app.lightmove.api.core.llm;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.retry.RetryTemplate;

/**
 * Spring AI's retry, which was already on and nobody had chosen its numbers.
 *
 * <p>{@code spring-ai-autoconfigure-retry} rides in on the GenAI starter and is gated only by
 * {@code @ConditionalOnClass}, so its {@code RetryTemplate} is active whether or not anyone asked —
 * and {@code GoogleGenAiChatModel}'s constructor takes one. Its default is <b>ten</b> attempts, which
 * against an unreachable Vertex, over a call that had no timeout, is a very long wait on a spinner.
 * This pins the numbers {@code application.yml} now sets, so a Spring AI upgrade that changes the
 * defaults cannot quietly restore them.
 */
@IntegrationTest
class SpringAiRetryConfigTest {

    @Autowired SpringAiRetryProperties retry;
    @Autowired RetryTemplate retryTemplate;

    @Test
    @DisplayName("one retry, not the framework's nine")
    void retriesOnce() {
        assertThat(retry.getMaxAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("the failures that will never come good are not retried")
    void doesNotRetryPermanentFailures() {
        // No credentials, a refused project, a bad request, an exhausted quota: retrying any of them
        // only delays the header-matcher fallback the caller was always going to get.
        assertThat(retry.getExcludeOnHttpCodes()).contains(400, 401, 403, 404, 429);
    }

    @Test
    @DisplayName("the template the chat model takes is actually in the context")
    void theTemplateExists() {
        // If this bean ever stops being published, the numbers above become decoration.
        assertThat(retryTemplate).isNotNull();
    }
}
