package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** Per-user budgets for the billed LLM endpoints — {@code lightmove.llm.rate-limit.*}. */
public record LlmRateLimitSettings(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("10") int shortlistRequestsPerMinute,
        @DefaultValue("20") int embedRequestsPerMinute
) {}
