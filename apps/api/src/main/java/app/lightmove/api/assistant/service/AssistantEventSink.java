package app.lightmove.api.assistant.service;

/**
 * Where a running turn reports progress.
 *
 * <p>The runner emits through this rather than reaching the repository, which keeps two things true:
 * the model call stays outside any transaction the worker holds, and a turn's side effects are
 * whatever the sink chooses to do with them — so a unit test can watch a turn stream without a
 * database.
 *
 * <p>It will grow when the tool surface lands: a tool round needs {@code toolCalled} and
 * {@code toolResult} interleaved with the text. The port is shaped now, with one implementation and
 * one caller, because that is the cheapest moment to get it right — and V65 gives {@code kind} no
 * CHECK precisely so those additions need no migration.
 */
public interface AssistantEventSink {

    /**
     * A batch of answer text, already a few hundred milliseconds' worth.
     *
     * <p>Called from the worker thread and expected to persist: this is not a per-token callback, and
     * the runner does the batching so the sink never has to decide whether a chunk is worth a row.
     */
    void delta(String text);
}
