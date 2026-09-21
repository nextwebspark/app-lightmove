package app.lightmove.api.core.config;

import java.time.Duration;
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
        @DefaultValue("12") int historyWindow,

        /**
         * How many rows one tool call may put in front of the model.
         *
         * <p>The assistant's number rather than the market's: a tool answer is spent twice, once in
         * the context window and again on the bill, and a mandate with four hundred companies is a
         * grid's worth of rows and not an answer's. A question needing more than this is one the
         * model should narrow and ask again.
         */
        @DefaultValue("25") int toolRowLimit,

        /**
         * Turns this instance will run at once, and therefore the spend cap.
         *
         * <p>Two against {@code --max-instances 2} is four concurrent Vertex calls fleet-wide, which
         * also sits comfortably inside {@code DB_POOL_MAX} of 5 once the appender's short writes are
         * counted. Raising it without raising the database tier trades a bill you can predict for a
         * connection pool you cannot.
         */
        @DefaultValue("2") int maxConcurrentTurns,

        /**
         * Turns that may wait for a slot before the accept path starts refusing with
         * {@code ASSISTANT_BUSY}. Deliberately shallow: a deep queue turns a capacity problem into a
         * latency problem, and a user who waited a minute for a slot has already given up.
         */
        @DefaultValue("8") int queueCapacity,

        /**
         * Whether the stranded-turn sweep runs on its timer.
         *
         * <p>Declared here rather than read only by {@code @ConditionalOnProperty} so the binder
         * knows the key: an unrecognised property under a bound namespace is a trap waiting for
         * whoever turns on strict binding. Off in the test profile, where a live timer would reclaim
         * other suites' in-flight turns — nothing rolls back there.
         */
        @DefaultValue("true") boolean sweepEnabled,

        /**
         * How long a RUNNING turn may go untouched before the sweep calls it stranded.
         *
         * <p>Generous against a 30–180s turn, because the cost of getting this wrong is asymmetric:
         * cancelling a turn that was merely slow throws away an answer already paid for, while
         * leaving a genuinely dead one a few minutes longer costs a reconnecting panel and nothing
         * else.
         */
        @DefaultValue("5m") Duration turnTimeout,

        /**
         * How often the sweep looks for stranded turns.
         *
         * <p>Declared here although {@code @Scheduled(fixedDelayString = …)} reads the same key
         * through a placeholder — it is resolved before any bean exists, so it cannot take this
         * value. The record's job is to make the key <b>known to the binder and validated</b>:
         * {@code sweepEnabled}'s javadoc above warns that an unrecognised property under a bound
         * namespace is a trap for whoever turns on strict binding, and this key was that trap.
         */
        @DefaultValue("1m") Duration sweepInterval
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
        if (toolRowLimit < 1) {
            throw new IllegalArgumentException(
                    "lightmove.assistant.tool-row-limit must be at least 1, but was " + toolRowLimit);
        }
        if (historyWindow < 0) {
            throw new IllegalArgumentException(
                    "lightmove.assistant.history-window must not be negative, but was " + historyWindow);
        }
        if (maxConcurrentTurns < 1) {
            throw new IllegalArgumentException(
                    "lightmove.assistant.max-concurrent-turns must be at least 1, but was "
                            + maxConcurrentTurns);
        }
        if (turnTimeout == null || turnTimeout.isZero() || turnTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "lightmove.assistant.turn-timeout must be positive, but was " + turnTimeout);
        }
        if (sweepInterval == null || sweepInterval.isZero() || sweepInterval.isNegative()) {
            throw new IllegalArgumentException(
                    "lightmove.assistant.sweep-interval must be positive, but was " + sweepInterval);
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException(
                    "lightmove.assistant.queue-capacity must not be negative, but was " + queueCapacity);
        }
    }
}
