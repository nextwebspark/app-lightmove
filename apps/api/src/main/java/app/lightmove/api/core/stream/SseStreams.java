package app.lightmove.api.core.stream;

/** Limits every reconnecting SSE endpoint in the application shares, whatever it streams. */
public final class SseStreams {

    /**
     * Under Cloud Run's request timeout, so streams close cleanly; shared because the frontend tells a scheduled close
     * from a break only while every reconnecting stream closes on the same schedule (the assistant's ask is not one).
     */
    public static final long STREAM_TIMEOUT_MS = 55_000;

    private SseStreams() {
    }
}
