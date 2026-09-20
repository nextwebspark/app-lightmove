package app.lightmove.api.assistant.constant;

/**
 * Where one exchange got to. Matches {@code app_lm_assistant_turn_status_chk} (V65) exactly — a name
 * this enum has and the CHECK does not is a constraint violation at write time, not a compile error.
 */
public enum AssistantTurnStatus {

    RUNNING,
    SUCCEEDED,

    /** The model or a tool failed. The question is still stored; the answer is not. */
    FAILED,

    /** Abandoned rather than failed — the caller went away, or a sweep reclaimed a stranded turn. */
    CANCELLED;

    public boolean isFinished() {
        return this != RUNNING;
    }
}
