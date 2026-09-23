package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tunables for the Uncava Assistant — {@code lightmove.assistant.*}. */
public record AssistantSettings(

        /**
         * Flash rather than Pro: an ask is one request held open while the model searches and
         * proposes, and it has to finish inside Cloud Run's 60s request timeout.
         */
        @DefaultValue("gemini-2.5-flash") String model,

        @DefaultValue("0.2") double temperature,

        @DefaultValue("512") int thinkingBudget,

        /** Earlier questions and answers of the chat sent with a new one. */
        @DefaultValue("12") int historyWindow,

        /** Rows one search may put in front of the model. */
        @DefaultValue("25") int toolRowLimit,

        /** Countries {@code describeMarket} lists — sized to be complete, not affordable. */
        @DefaultValue("250") int vocabularyLimit
) {

    public AssistantSettings {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("lightmove.assistant.model must be set");
        }
        if (temperature < 0) {
            throw new IllegalArgumentException(
                    "lightmove.assistant.temperature must not be negative, but was " + temperature);
        }
        if (thinkingBudget < 0) {
            throw new IllegalArgumentException(
                    "lightmove.assistant.thinking-budget must not be negative, but was " + thinkingBudget);
        }
        if (historyWindow < 0) {
            throw new IllegalArgumentException(
                    "lightmove.assistant.history-window must not be negative, but was " + historyWindow);
        }
        if (toolRowLimit < 1) {
            throw new IllegalArgumentException(
                    "lightmove.assistant.tool-row-limit must be at least 1, but was " + toolRowLimit);
        }
        if (vocabularyLimit < 1) {
            throw new IllegalArgumentException(
                    "lightmove.assistant.vocabulary-limit must be at least 1, but was " + vocabularyLimit);
        }
    }
}
