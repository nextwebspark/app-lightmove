package app.lightmove.api.core.resilience.model;

/**
 * Which vendor was asked what. Carried into every failure so a log line names the call rather than
 * the stack frame that happened to raise it.
 */
public record VendorCall(String vendor, String operation) {

    public static VendorCall of(String vendor, String operation) {
        return new VendorCall(vendor, operation);
    }

    @Override
    public String toString() {
        return vendor + "/" + operation;
    }
}
