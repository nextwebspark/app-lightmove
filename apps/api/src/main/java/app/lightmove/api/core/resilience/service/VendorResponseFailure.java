package app.lightmove.api.core.resilience.service;

import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import lombok.Getter;

/**
 * A classified non-2xx, thrown by the status handler where the response is still open and completed
 * into a {@link app.lightmove.api.core.resilience.model.VendorException} by {@link VendorCallGuard},
 * which is the layer that knows what was being asked.
 */
@Getter
class VendorResponseFailure extends RuntimeException {

    private final VendorFailureKind kind;

    VendorResponseFailure(VendorFailureKind kind) {
        super(kind.name());
        this.kind = kind;
    }
}
