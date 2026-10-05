package app.lightmove.api.core.logging.service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The trace Cloud Run hands the container, read from {@code traceparent} or {@code X-Cloud-Trace-Context}.
 *
 * <p>Both headers are caller-suppliable, so anything not exactly the documented shape is dropped: a
 * caller choosing their own trace id may at most nest their own lines, never write into the JSON.
 *
 * @param spanIdHex 16 lowercase hex chars — the only spelling {@code logging.googleapis.com/spanId} matches
 */
public record CloudTraceContext(String traceId, String spanIdHex, boolean sampled) {

    private static final Pattern TRACEPARENT =
            Pattern.compile("^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");
    private static final Pattern CLOUD_TRACE_CONTEXT =
            Pattern.compile("^([0-9a-f]{32})/(\\d{1,20})(?:;o=([01]))?$");
    private static final String ZERO_TRACE_ID = "0".repeat(32);
    private static final String ZERO_SPAN_ID = "0".repeat(16);

    public static Optional<CloudTraceContext> parse(String traceparent, String cloudTraceContext) {
        Optional<CloudTraceContext> fromW3c = traceparent == null ? Optional.empty() : fromTraceparent(traceparent);
        return fromW3c.or(() -> cloudTraceContext == null ? Optional.empty() : fromCloudTraceContext(cloudTraceContext));
    }

    private static Optional<CloudTraceContext> fromTraceparent(String header) {
        Matcher match = TRACEPARENT.matcher(header.trim());
        if (!match.matches() || match.group(1).equals(ZERO_TRACE_ID) || match.group(2).equals(ZERO_SPAN_ID)) {
            return Optional.empty();
        }
        boolean sampled = (Integer.parseInt(match.group(3), 16) & 1) == 1;
        return Optional.of(new CloudTraceContext(match.group(1), match.group(2), sampled));
    }

    /** Its span id is a decimal unsigned 64-bit integer; logged as-is it silently matches no span. */
    private static Optional<CloudTraceContext> fromCloudTraceContext(String header) {
        Matcher match = CLOUD_TRACE_CONTEXT.matcher(header.trim());
        if (!match.matches() || match.group(1).equals(ZERO_TRACE_ID)) {
            return Optional.empty();
        }
        long spanId;
        try {
            spanId = Long.parseUnsignedLong(match.group(2));
        } catch (NumberFormatException overflow) {
            return Optional.empty();
        }
        if (spanId == 0) {
            return Optional.empty();
        }
        return Optional.of(new CloudTraceContext(match.group(1), String.format("%016x", spanId),
                "1".equals(match.group(3))));
    }
}
