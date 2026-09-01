package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tunables for the Google GenAI integration — {@code lightmove.llm.*}. */
public record LlmSettings(
        LlmRateLimitSettings rateLimit,

        /**
         * Ceiling on one call to the model, in milliseconds.
         *
         * <p>The Google SDK applies no bound of its own that we set, and Spring AI's retry sits on top
         * of the call rather than around the clock — so without this a hung Vertex holds a request
         * thread, and the user's browser, for as long as it likes.
         */
        @DefaultValue("20000") int requestTimeoutMs
) {

    public LlmSettings {
        if (requestTimeoutMs < 1) {
            throw new IllegalArgumentException(
                    "lightmove.llm.request-timeout-ms must be positive, but was " + requestTimeoutMs);
        }
    }
}
