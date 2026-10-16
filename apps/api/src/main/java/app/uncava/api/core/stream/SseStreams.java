package app.uncava.api.core.stream;

/** Limits every reconnecting SSE endpoint in the application shares, whatever it streams. */
public final class SseStreams {

    /**
     * Well under Cloud Run's request timeout ({@code --timeout} in {@code ops/gcp/deploy.sh}), so the
     * server ends every stream cleanly and the browser reconnects on a normal close instead of a
     * mid-air network error.
     *
     * <p>Shared rather than redeclared per stream: the frontend distinguishes "the server closed on
     * schedule" from "the connection broke" by whether it saw the opening frame — which only works
     * while every reconnecting stream closes on the same schedule. The assistant's ask is not one of
     * them: it is a single answer with its own deadline ({@code uncava.assistant.answer-deadline}).
     */
    public static final long STREAM_TIMEOUT_MS = 55_000;

    private SseStreams() {
    }
}
