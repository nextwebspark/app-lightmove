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

    /** The trace headers Cloud Run's front end sets — caller-suppliable, so {@link CloudTraceContext} parses them strictly. */
    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String CLOUD_TRACE_CONTEXT_HEADER = "X-Cloud-Trace-Context";

    /** MDC keys the deployed encoder renames to Cloud Logging's {@code logging.googleapis.com/*} trace fields. */
    public static final String TRACE_KEY = "gcpTrace";
    public static final String SPAN_ID_KEY = "gcpSpanId";
    public static final String TRACE_SAMPLED_KEY = "gcpTraceSampled";

    /** Who and which tenant a line belongs to — ids only, never an email or a name. */
    public static final String USER_ID_KEY = "userId";
    public static final String WORKSPACE_ID_KEY = "workspaceId";
    public static final String PROJECT_ID_KEY = "projectId";

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
}
