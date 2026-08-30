package app.lightmove.api.core.vendor.service;

import app.lightmove.api.core.vendor.model.VendorFailure;
import lombok.Getter;

/**
 * A classified non-2xx, thrown from inside the {@code RestClient} status handler.
 *
 * <p>Package-private and short-lived: it exists only because the handler is built once per vendor
 * while a {@code VendorCall} names one operation, so the handler knows <i>what went wrong</i> but not
 * <i>what was being asked</i>. {@link VendorCallGuard} catches this and completes it into a
 * {@code VendorException}, which is the only outbound failure type anything else ever sees.
 *
 * <p>The alternative — passing the call down through a thread local, or rebuilding the client per
 * operation — buys nothing this does not.
 */
@Getter
class VendorResponseFailure extends RuntimeException {

    private final transient VendorFailure failure;

    VendorResponseFailure(VendorFailure failure) {
        super(failure.describe(), null, false, false);
        this.failure = failure;
    }
}
