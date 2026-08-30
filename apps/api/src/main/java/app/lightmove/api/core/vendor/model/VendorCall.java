package app.lightmove.api.core.vendor.model;

/**
 * One named operation against one vendor — the label carried through every log line, retry decision
 * and exception this layer produces.
 *
 * <p>It exists as a type rather than two loose strings because those two strings travel together
 * everywhere, and because {@code idempotent} has to travel with them: whether a timed-out call may be
 * repeated is a property of the operation, not of the failure. A search that reads may be retried
 * freely; anything that spends a credit per call and might already have been processed may not.
 *
 * @param vendor    who is being called, lowercase and stable — it becomes a rate-limit key
 * @param operation what is being asked of them, for the log and the retry label
 * @param idempotent whether repeating this exact call is harmless
 */
public record VendorCall(String vendor, String operation, boolean idempotent) {

    /** A read. Safe to repeat, which is what makes a timeout retryable. */
    public static VendorCall read(String vendor, String operation) {
        return new VendorCall(vendor, operation, true);
    }

    /** A call that may have taken effect before it timed out, so it is never retried on a timeout. */
    public static VendorCall write(String vendor, String operation) {
        return new VendorCall(vendor, operation, false);
    }

    /** Reads {@code vendor.operation} — the retry label and the metric name this layer would use. */
    public String label() {
        return vendor + "." + operation;
    }
}
