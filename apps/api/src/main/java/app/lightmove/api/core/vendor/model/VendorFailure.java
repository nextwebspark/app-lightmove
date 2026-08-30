package app.lightmove.api.core.vendor.model;

import app.lightmove.api.core.vendor.constant.VendorFailureKind;
import java.time.Duration;

/**
 * What went wrong on one attempt, reduced to the things a decision may be made from.
 *
 * <p>{@code bodySnippet} is the one field here that is dangerous. A vendor's 4xx body routinely
 * echoes the query back — a name, a company, a LinkedIn URL — so it is personal data, it is off
 * unless a vendor opts in, and it must never reach a log. It is held for a developer reading a
 * failing test, not for production diagnostics.
 *
 * @param kind       the classification every retry and cascade decision reads
 * @param status     the HTTP status, or null when the vendor never answered
 * @param retryAfter what they asked us to wait, when they said
 * @param bodySnippet a truncated, opt-in copy of the error body; never logged
 */
public record VendorFailure(VendorFailureKind kind, Integer status, Duration retryAfter, String bodySnippet) {

    public static VendorFailure of(VendorFailureKind kind) {
        return new VendorFailure(kind, null, null, null);
    }

    /**
     * Whether this attempt is worth repeating, given what was being attempted.
     *
     * <p>Two refinements the kind alone cannot make. A timeout is only retryable for an idempotent
     * call, because a write that timed out may already have been charged. And a {@code Retry-After}
     * longer than we are willing to hold a thread turns a retryable failure into a permanent one —
     * the header cannot shorten the wait (Spring's backoff cannot read it), so the honest response to
     * "come back in five minutes" is to stop, not to sleep through it.
     */
    public boolean worthRetrying(VendorCall call, Duration retryAfterCeiling) {
        if (!kind.retryable()) {
            return false;
        }
        if (kind == VendorFailureKind.TIMEOUT && !call.idempotent()) {
            return false;
        }
        return retryAfter == null || retryAfter.compareTo(retryAfterCeiling) <= 0;
    }

    /** Safe for a log line: the classification and the status, never the body. */
    public String describe() {
        return status == null ? kind.name() : kind + " (HTTP " + status + ")";
    }
}
