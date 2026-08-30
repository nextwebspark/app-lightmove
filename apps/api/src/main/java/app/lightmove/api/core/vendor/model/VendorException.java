package app.lightmove.api.core.vendor.model;

import lombok.Getter;

/**
 * A third-party API did not give us the answer we paid for.
 *
 * <p>Every outbound failure arrives as one of these, already classified, so no caller ever branches
 * on an HTTP status or catches a {@code RestClientException}. That is the whole point of the layer:
 * the decision to retry, to fall through to another endpoint, or to stop is made from
 * {@link VendorFailure#kind()} and nothing else.
 *
 * <p><b>The message is internal and stays internal.</b> Same discipline as {@code ApiException}: what
 * a vendor says about a failed request is diagnostic detail that may quote the query — a person's
 * name, a company — so it reaches the log's classification fields and never an API response body. A
 * caller that wants to tell a user something translates to an {@code ApiException} at its own seam.
 *
 * <p>One case this layer cannot see for you: a vendor that answers <b>HTTP 200 with an error
 * document</b>. No status handler can classify that, so an adapter reading a body that carries its
 * own error field is responsible for throwing this itself.
 */
@Getter
public class VendorException extends RuntimeException {

    private final transient VendorCall call;
    private final transient VendorFailure failure;

    public VendorException(VendorCall call, VendorFailure failure, String internalDetail) {
        super("%s: %s — %s".formatted(call.label(), failure.describe(), internalDetail));
        this.call = call;
        this.failure = failure;
    }

    public VendorException(VendorCall call, VendorFailure failure, Throwable cause) {
        super("%s: %s".formatted(call.label(), failure.describe()), cause);
        this.call = call;
        this.failure = failure;
    }
}
