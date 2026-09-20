package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tunables for the Uncava Assistant — {@code lightmove.assistant.*}. */
public record AssistantSettings(

        /**
         * The model one assistant turn runs on, named here rather than taken from
         * {@code spring.ai.google.genai.chat.model}.
         *
         * <p>That one is the application's default and serves the narrow prompts — a column mapping,
         * a field extraction — which a Flash-tier model answers well. A turn is a different workload:
         * it plans over a wide tool surface and decides what to call in what order, which is the axis
         * a Pro-tier model is actually better at. Two workloads, two models, and neither should move
         * because the other needed to.
         */
        @DefaultValue("gemini-3.1-pro") String model,

        /**
         * Low, because a turn's job is to choose correctly rather than to write well: the same
         * question twice should reach for the same tools.
         */
        @DefaultValue("0.2") double temperature,

        /**
         * Thinking tokens per turn. Deliberately not zero, which is what the extraction prompts use —
         * they classify, and this one plans.
         */
        @DefaultValue("2048") int thinkingBudget,

        /**
         * How many earlier exchanges of the thread travel with a question.
         *
         * <p>A window rather than the whole thread, because Gemini has no server-side compaction and
         * an unbounded history is an unbounded bill. Summarising what falls off the back is its own
         * problem and not this one's.
         */
        @DefaultValue("12") int historyWindow
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
    }
}
