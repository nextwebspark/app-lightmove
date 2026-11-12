package app.lightmove.api.core.resilience.service;

import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import lombok.Getter;

/**
 * A classified non-2xx, thrown by the status handler where the response is still open and completed
 * into a {@link app.lightmove.api.core.resilience.model.VendorException} by {@link VendorCallGuard},
 * which is the layer that knows what was being asked. {@code detail} is what the log may say about the
 * refusal — the vendor's code, or the body's shape when it gave none — and never the body's words.
 */
@Getter
class VendorResponseFailure extends RuntimeException {

    private final VendorFailureKind kind;
    private final String vendorCode;
    private final String detail;

    VendorResponseFailure(VendorFailureKind kind, String vendorCode, String detail) {
        super(detail == null ? kind.name() : kind.name() + " (" + detail + ")");
        this.kind = kind;
        this.vendorCode = vendorCode;
        this.detail = detail;
    }
}
