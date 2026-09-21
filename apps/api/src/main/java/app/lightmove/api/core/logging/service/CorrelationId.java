package app.lightmove.api.core.logging.service;

import org.slf4j.MDC;

/**
 * The id that ties one request's log lines, audit events and error response together.
 *
 * <p>Held in the SLF4J {@link MDC} so every log line picks it up without a single call site passing
 * it around. When a customer quotes the id from an error page, it is the only thing needed to find
 * exactly what happened.
 */
public final class CorrelationId {

    public static final String MDC_KEY = "correlationId";
    public static final String HEADER = "X-Correlation-Id";

    private CorrelationId() {
    }

    /** Never null: an error response with no id is worse than one with an unrecognised id. */
    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value != null ? value : "none";
    }

    static void set(String value) {
        MDC.put(MDC_KEY, value);
    }

    /**
     * Takes on an id resolved elsewhere, for a thread that has no request of its own.
     *
     * <p>A background worker's log lines and audit row would otherwise read {@code "none"}, because
     * {@code current()} answers from MDC and the filter that populates it never ran. The id is read
     * back from the row the accepting request wrote, so it stays the caller's own rather than being
     * invented. Always paired with {@link #release()} in a finally block: these threads are pooled,
     * so an id left behind is attributed to whatever runs next.
     */
    public static void adopt(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            set(correlationId);
        }
    }

    /** Hands back an adopted id. See {@link #adopt(String)} for why this is not optional. */
    public static void release() {
        clear();
    }

    static void clear() {
        MDC.remove(MDC_KEY);
    }
}
