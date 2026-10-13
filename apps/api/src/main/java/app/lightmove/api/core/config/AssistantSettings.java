package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tunables for the Uncava Assistant — {@code lightmove.assistant.*}. */
public record AssistantSettings(

        /** Flash rather than Pro: an ask is one request held open while the model searches and proposes. */
        @DefaultValue("gemini-3.5-flash-lite") String model,

        @DefaultValue("0.2") double temperature,

        @DefaultValue("512") int thinkingBudget,

        /** Earlier questions and answers of the chat sent with a new one. */
        @DefaultValue("12") int historyWindow,

        /** Rows one search may put in front of the model. */
        @DefaultValue("25") int toolRowLimit,

        /** Countries {@code describeMarket} lists — sized to be complete, not affordable. */
        @DefaultValue("250") int vocabularyLimit,

        /** Answers one instance works on at once; the next ask is refused rather than queued. */
        @DefaultValue("8") int maxConcurrentAsks,

        /** Names one lookup checks. */
        @DefaultValue("10") int maxNamesPerLookup,

        /**
         * Names checked at once. A lookup holds a connection only for each indexed name query, never across
         * a vendor call, so a deployed pool of five queues briefly rather than running dry.
         */
        @DefaultValue("5") int nameLookupParallelism,

        /** How long a lookup waits for its names before reporting the rest as not checked. */
        @DefaultValue("25s") Duration nameLookupDeadline,

        /** Billed Bright Data name searches one answer may make, whatever the model asks for. */
        @DefaultValue("15") int maxVendorSearchesPerAsk,

        /** When an ask's stream reports the answer as still being written; no model round starts after it. */
        @DefaultValue("150s") Duration answerDeadline,

        /** Tools every ask of one instance runs at once, held under the deployed pool of five connections. */
        @DefaultValue("4") int toolParallelism,

        /** How long a model round is waited for before it is asked again; its last attempt is not held to it. */
        @DefaultValue("12s") Duration roundPatience
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
        if (maxConcurrentAsks < 1 || maxNamesPerLookup < 1 || nameLookupParallelism < 1
                || maxVendorSearchesPerAsk < 0 || toolParallelism < 1) {
            throw new IllegalArgumentException("lightmove.assistant's ask, name and search limits must be positive");
        }
        if (nameLookupDeadline == null || nameLookupDeadline.isNegative() || nameLookupDeadline.isZero()) {
            throw new IllegalArgumentException("lightmove.assistant.name-lookup-deadline must be positive");
        }
        if (answerDeadline == null || answerDeadline.isNegative() || answerDeadline.isZero()) {
            throw new IllegalArgumentException("lightmove.assistant.answer-deadline must be positive");
        }
        if (roundPatience == null || roundPatience.isNegative() || roundPatience.isZero()) {
            throw new IllegalArgumentException("lightmove.assistant.round-patience must be positive");
        }
        if (vocabularyLimit < 1) {
            throw new IllegalArgumentException(
                    "lightmove.assistant.vocabulary-limit must be at least 1, but was " + vocabularyLimit);
        }
    }
}
