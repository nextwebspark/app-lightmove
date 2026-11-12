package app.lightmove.api.core.resilience.model;

import java.time.Duration;

/**
 * Which vendor was asked what. Carried into every failure so a log line names the call rather than
 * the stack frame that happened to raise it. {@code maxWait} is how long the call may queue for a rate
 * permit; null takes {@code lightmove.resilience.permit-max-wait}, which suits a request thread.
 */
public record VendorCall(String vendor, String operation, Duration maxWait) {

    public static VendorCall of(String vendor, String operation) {
        return new VendorCall(vendor, operation, null);
    }

    /** For a background job, which would rather queue behind its own earlier calls than fail. */
    public VendorCall waitingUpTo(Duration wait) {
        return new VendorCall(vendor, operation, wait);
    }

    @Override
    public String toString() {
        return vendor + "/" + operation;
    }
}
