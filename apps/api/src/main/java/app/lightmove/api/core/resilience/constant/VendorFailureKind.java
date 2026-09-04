package app.lightmove.api.core.resilience.constant;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.UnknownContentTypeException;

/**
 * Why a call to a paid third-party API did not produce an answer.
 *
 * <p>This is {@code ErrorCode} pointed outward. Inbound, one enum decides the status and the sentence
 * a client reads; outbound, one enum decides the only question that matters about a failure — should
 * we pay to try again. {@code if (status == 429)} repeated across every adapter is one chance per
 * adapter to get it wrong, and getting it wrong here spends money.
 */
public enum VendorFailureKind {

    /** 401/403. The key is wrong, expired, or lacks the plan. Every endpoint fails identically. */
    CREDENTIALS(false),

    /** 402. Out of credits; retrying spends time to be told so again. */
    QUOTA_EXHAUSTED(false),

    /** 400/422. Malformed, which makes it our bug rather than a bad minute. */
    BAD_REQUEST(false),

    /** 404, or a 200 carrying no rows. Not a failure but an answer, and answered as an empty result. */
    NOT_FOUND(false),

    /** 429. Waiting is the fix. */
    RATE_LIMITED(true),

    /** 5xx. Their bad minute. */
    UNAVAILABLE(true),

    /** A connect or read timeout, or a dropped connection. */
    TIMEOUT(true),

    /** A 2xx body that would not deserialise. Re-reading gets the same broken body. */
    MALFORMED_RESPONSE(false);

    private final boolean retryable;

    VendorFailureKind(boolean retryable) {
        this.retryable = retryable;
    }

    /** Whether repeating the identical call could plausibly succeed. */
    public boolean retryable() {
        return retryable;
    }

    /** The vendor answered, and the status says what it thought of the request. */
    public static VendorFailureKind of(HttpStatusCode status) {
        return switch (status.value()) {
            case 401, 403 -> CREDENTIALS;
            case 402 -> QUOTA_EXHAUSTED;
            case 404 -> NOT_FOUND;
            case 429 -> RATE_LIMITED;
            default -> status.is5xxServerError() ? UNAVAILABLE : BAD_REQUEST;
        };
    }

    /**
     * The vendor did not answer, or answered something unreadable — neither of which ever reaches a
     * status handler, because in both cases there is no status to hand it. {@code RestClient} raises
     * {@link ResourceAccessException} for a transport failure before any response exists, and wraps a
     * conversion failure as a plain {@link RestClientException}; classifying from status codes alone
     * would leave both unclassified and therefore un-retried.
     */
    public static VendorFailureKind of(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpConnectTimeoutException || cause instanceof HttpTimeoutException
                    || cause instanceof SocketTimeoutException) {
                return TIMEOUT;
            }
            if (cause instanceof UnknownContentTypeException) {
                return MALFORMED_RESPONSE;
            }
            if (cause instanceof ResourceAccessException || cause instanceof IOException) {
                return TIMEOUT;
            }
        }
        return MALFORMED_RESPONSE;
    }
}
