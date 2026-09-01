package app.lightmove.api.core.config;

/** Tunables for the Google GenAI integration — {@code lightmove.llm.*}. */
public record LlmSettings(
        LlmRateLimitSettings rateLimit
) {}
