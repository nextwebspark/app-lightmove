package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tunables for the Uncava Assistant — {@code lightmove.assistant.*}. */
public record AssistantSettings(

        /** Flash rather than Pro: an ask is one request held open while the model searches and proposes. */
        @DefaultValue("gemini-2.5-flash") String model,

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

        /**
         * How long an ask's stream stays open before it reports the answer as still being written, and
         * after which no further model round starts. Must stay under Cloud Run's request timeout
         * ({@code --timeout} in {@code ops/gcp/deploy.sh}) with room for the round in flight.
         */
        @DefaultValue("150s") Duration answerDeadline
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
                || maxVendorSearchesPerAsk < 0) {
            throw new IllegalArgumentException("lightmove.assistant's ask, name and search limits must be positive");
        }
        if (nameLookupDeadline == null || nameLookupDeadline.isNegative() || nameLookupDeadline.isZero()) {
            throw new IllegalArgumentException("lightmove.assistant.name-lookup-deadline must be positive");
        }
        if (answerDeadline == null || answerDeadline.isNegative() || answerDeadline.isZero()) {
            throw new IllegalArgumentException("lightmove.assistant.answer-deadline must be positive");
        }
        if (vocabularyLimit < 1) {
            throw new IllegalArgumentException(
                    "lightmove.assistant.vocabulary-limit must be at least 1, but was " + vocabularyLimit);
        }
    }
}
