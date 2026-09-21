package app.lightmove.api.assistant.service;

/**
 * Where a running turn reports progress.
 *
 * <p>The runner emits through this rather than reaching the repository, which keeps two things true:
 * the model call stays outside any transaction the worker holds, and a turn's side effects are
 * whatever the sink chooses to do with them — so a unit test can watch a turn stream without a
 * database.
 *
 * <p>The tool pair below arrived with the tool surface, as this port said it would, and cost no
 * migration: V65 gives {@code kind} no CHECK precisely so a new kind is a constant rather than a
 * schema change.
 */
public interface AssistantEventSink {

    /**
     * A batch of answer text, already a few hundred milliseconds' worth.
     *
     * <p>Called from the worker thread and expected to persist: this is not a per-token callback, and
     * the runner does the batching so the sink never has to decide whether a chunk is worth a row.
     */
    void delta(String text);

    /**
     * The model asked for a tool, before the tool runs.
     *
     * <p>Default rather than abstract so the two lambdas that predate the tool surface — the worker's
     * own sink and the runner's unit test — keep compiling. A sink that only wants the text is a
     * legitimate sink, and one that silently dropped a call it meant to record would be worse.
     *
     * @param arguments the model's own JSON, recorded as sent and never as authorised
     */
    default void toolCalled(String toolName, String arguments) {
    }

    /** What the tool answered, a refusal included. */
    default void toolResult(String toolName, String result) {
    }
}
