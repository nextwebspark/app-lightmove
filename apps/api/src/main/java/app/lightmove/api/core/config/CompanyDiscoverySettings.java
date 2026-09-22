package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Strategy's AI Research — {@code lightmove.company.discovery.*}.
 *
 * <p>Beside {@code lightmove.company.search.*} rather than under {@code lightmove.llm}, because what
 * is being configured is a read of the company market that happens to reach the open web; the model
 * is how, not what.
 */
public record CompanyDiscoverySettings(

        /**
         * Whether this deployment offers discovery at all. Off leaves the port answering nothing and
         * the toolbar's CTA disabled, rather than a button that fails when pressed.
         */
        @DefaultValue("true") boolean enabled,

        /**
         * The model a grounded search runs on. Its own key for the reason
         * {@code lightmove.assistant.model} is: this is a lookup with a web tool attached, which a
         * Flash-tier model answers well, and it should not move because a planning workload needed a
         * Pro-tier one.
         */
        @DefaultValue("gemini-2.5-flash") String model,

        /** Low: a market question asked twice should reach for the same companies. */
        @DefaultValue("0.2") double temperature,

        /** Zero, matching the extraction prompts — this retrieves and classifies, it does not plan. */
        @DefaultValue("0") int thinkingBudget,

        /**
         * Whether to attempt grounding and a response schema on one call.
         *
         * <p>Vertex has historically refused the pair. {@code auto} tries it once per process and
         * latches off on a refusal that names the schema; {@code off} never tries and goes straight
         * to grounded prose plus a second ungrounded extraction. A deployment that knows its region's
         * answer sets this rather than paying for the probe.
         */
        @DefaultValue("true") boolean groundedStructuredOutput,

        /** Companies returned when the request names no explicit {@code limit}. */
        @DefaultValue("10") int defaultResultLimit,

        /** Hard ceiling on companies one search returns; a larger requested {@code limit} is refused. */
        @DefaultValue("25") int maxResultLimit,

        /** Longest accepted question. A scope, not an attack: a market question is a sentence. */
        @DefaultValue("500") int maxQuestionLength,

        /**
         * Searches one workspace may run per UTC day, held by {@code app_lm_workspace_daily_spend}
         * rather than in a heap, because this is the number standing between a firm and a bill.
         */
        @DefaultValue("25") int dailySearchesPerWorkspace
) {

    public CompanyDiscoverySettings {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("lightmove.company.discovery.model must be set");
        }
        if (temperature < 0) {
            throw new IllegalArgumentException(
                    "lightmove.company.discovery.temperature must not be negative, but was " + temperature);
        }
        if (thinkingBudget < 0) {
            throw new IllegalArgumentException(
                    "lightmove.company.discovery.thinking-budget must not be negative, but was "
                            + thinkingBudget);
        }
        if (defaultResultLimit < 1 || defaultResultLimit > maxResultLimit) {
            throw new IllegalArgumentException(
                    "lightmove.company.discovery.default-result-limit must be between 1 and "
                            + maxResultLimit + ", but was " + defaultResultLimit);
        }
        if (maxQuestionLength < 1) {
            throw new IllegalArgumentException(
                    "lightmove.company.discovery.max-question-length must be at least 1, but was "
                            + maxQuestionLength);
        }
        // Zero would not disable the feature, it would admit the day's first search and refuse the
        // rest — see WorkspaceDailySpend.CLAIM. Turning it off is what `enabled: false` is for.
        if (dailySearchesPerWorkspace < 1) {
            throw new IllegalArgumentException(
                    "lightmove.company.discovery.daily-searches-per-workspace must be at least 1 "
                            + "(set enabled: false to turn discovery off), but was "
                            + dailySearchesPerWorkspace);
        }
    }
}
