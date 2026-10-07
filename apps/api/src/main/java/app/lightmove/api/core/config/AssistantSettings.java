package app.lightmove.api.core.config;

import java.time.Duration;
import java.util.List;
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
        @DefaultValue("250") int vocabularyLimit,

        /** Answers one instance works on at once; the next ask is refused rather than queued. */
        @DefaultValue("4") int maxConcurrentAsks,

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

        /** Specialists the supervisor may ask in one answer — each is a nested model call. */
        @DefaultValue("3") int maxSpecialistCallsPerAsk,

        /**
         * The universe size an answer's refinements steer toward: the mandate's In universe and Shortlisted
         * companies plus the new ones the search found.
         */
        @DefaultValue("50") int targetUniverseMin,

        @DefaultValue("75") int targetUniverseMax,

        /** ISO-2 codes offered beside a search's own countries when it finds too few — the Gulf six. */
        @DefaultValue({"AE", "SA", "QA", "KW", "BH", "OM"}) List<String> refinementNeighbourCountryCodes
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
                || maxVendorSearchesPerAsk < 0 || maxSpecialistCallsPerAsk < 1) {
            throw new IllegalArgumentException("lightmove.assistant's ask, name and search limits must be positive");
        }
        if (nameLookupDeadline == null || nameLookupDeadline.isNegative() || nameLookupDeadline.isZero()) {
            throw new IllegalArgumentException("lightmove.assistant.name-lookup-deadline must be positive");
        }
        if (targetUniverseMin < 1 || targetUniverseMax < targetUniverseMin) {
            throw new IllegalArgumentException("lightmove.assistant.target-universe-min must be at least 1 and at most "
                    + "target-universe-max, but was " + targetUniverseMin + "–" + targetUniverseMax);
        }
        refinementNeighbourCountryCodes = refinementNeighbourCountryCodes == null ? List.of()
                : refinementNeighbourCountryCodes.stream().filter(code -> code != null && !code.isBlank()).toList();
        if (vocabularyLimit < 1) {
            throw new IllegalArgumentException(
                    "lightmove.assistant.vocabulary-limit must be at least 1, but was " + vocabularyLimit);
        }
    }
}
