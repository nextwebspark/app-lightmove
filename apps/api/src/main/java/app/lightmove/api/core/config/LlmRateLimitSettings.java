package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** Per-user budgets for the billed LLM endpoints — {@code lightmove.llm.rate-limit.*}. */
public record LlmRateLimitSettings(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("10") int shortlistRequestsPerMinute,
        @DefaultValue("20") int embedRequestsPerMinute,

        /** Every other meter (column mapping, each extraction step): one deliberate click either way,
         *  so a second knob per meter is a second thing to get wrong, but each still its own budget so
         *  none of them can eat another's calls. */
        @DefaultValue("10") int defaultRequestsPerMinute
) {}
