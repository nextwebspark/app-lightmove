package app.lightmove.api.notetaker.constant;

/** A suggestion waits until a researcher applies or skips it; an undo puts it back to waiting. */
public enum CallSuggestionStatus {
    PENDING,
    APPLIED,
    SKIPPED
}
