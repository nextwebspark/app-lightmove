package app.lightmove.api.core.http.model;

import app.lightmove.api.core.http.constant.GuardedFetchFailureKind;

/** A guarded fetch that got no answer to read; the message is internal detail, never shown. */
public class GuardedFetchFailure extends RuntimeException {

    private final GuardedFetchFailureKind kind;

    public GuardedFetchFailure(GuardedFetchFailureKind kind, String detail, Throwable cause) {
        super(detail, cause);
        this.kind = kind;
    }

    public GuardedFetchFailureKind kind() {
        return kind;
    }
}
