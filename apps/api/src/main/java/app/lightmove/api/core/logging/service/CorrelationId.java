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

    static void clear() {
        MDC.remove(MDC_KEY);
    }
}
