package app.lightmove.api.core.resilience.model;

import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import lombok.Getter;

/**
 * The only failure a vendor adapter ever throws, and the only one its callers ever catch.
 *
 * <p>The message names the call and the kind and never the vendor's own error body: those bodies echo
 * the query, so a Bright Data 400 quotes the person being researched. Keeping the body out of the
 * message is what keeps a name out of the logs.
 */
@Getter
public class VendorException extends RuntimeException {

    private final transient VendorCall call;
    private final VendorFailureKind kind;

    public VendorException(VendorCall call, VendorFailureKind kind, Throwable cause) {
        super(call + " failed: " + kind, cause);
        this.call = call;
        this.kind = kind;
    }
}
