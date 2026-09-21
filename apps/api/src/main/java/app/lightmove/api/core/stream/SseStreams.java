package app.lightmove.api.core.stream;

/** Limits every SSE endpoint in the application shares, whatever it streams. */
public final class SseStreams {

    /**
     * Just under Cloud Run's request timeout (`--timeout 60s` in {@code ops/gcp/deploy.sh}), so the
     * server ends every stream cleanly and the browser reconnects on a normal close instead of a
     * mid-air network error.
     *
     * <p>Shared rather than redeclared per stream: two endpoints picking their own 55s independently
     * would drift the moment somebody changed the deploy flag, and the frontend distinguishes "the
     * server closed on schedule" from "the connection broke" by whether it saw the opening frame —
     * which only works while every stream closes on the same schedule.
     */
    public static final long STREAM_TIMEOUT_MS = 55_000;

    private SseStreams() {
    }
}
