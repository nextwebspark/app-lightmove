package app.lightmove.api.core.resilience.model;

import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import lombok.Getter;

/**
 * The only failure a vendor adapter throws. Never carries the vendor's error body — it echoes the query, putting a
 * researched person's name in the logs — only its single-token machine code, or the body's shape and request id.
 */
@Getter
public class VendorException extends RuntimeException {

    private final transient VendorCall call;
    private final VendorFailureKind kind;
    private final String vendorCode;

    public VendorException(VendorCall call, VendorFailureKind kind, Throwable cause) {
        this(call, kind, null, cause);
    }

    public VendorException(VendorCall call, VendorFailureKind kind, String vendorCode, Throwable cause) {
        this(call, kind, vendorCode, vendorCode, cause);
    }

    /** {@code detail} is what the log may say beside the kind: the vendor's code, or its body's shape without one. */
    public VendorException(VendorCall call, VendorFailureKind kind, String vendorCode, String detail,
                           Throwable cause) {
        super(call + " failed: " + kind + (detail == null ? "" : " (" + detail + ")"), cause);
        this.call = call;
        this.kind = kind;
        this.vendorCode = vendorCode;
    }
}
