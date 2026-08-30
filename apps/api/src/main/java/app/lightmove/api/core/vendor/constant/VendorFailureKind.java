package app.lightmove.api.core.vendor.constant;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.UnknownContentTypeException;

/**
 * Why a call to a paid third-party API did not produce an answer, as a stable classification.
 *
 * <p>This is {@code ErrorCode} pointed outward. Inbound, one enum decides the status and the sentence
 * a client reads; outbound, one enum decides the only two questions that matter about a failure:
 * <b>should we try again</b>, and <b>should we try a different endpoint</b>. Both exist because a
 * policy needs a vocabulary — {@code if (status == 429)} repeated across every adapter is one chance
 * per adapter to get it wrong, and getting it wrong here spends money.
 *
 * <p>The two mapping functions are deliberately pure statics rather than logic inside an HTTP
 * handler. Reading an answer and fetching one are separate jobs, and the reading half is the half
 * that is worth testing exhaustively — the same split, for the same reason, as
 * {@code EmailAddressValidator.acceptsMail}.
 */
public enum VendorFailureKind {

    /** 401/403. The key is wrong, expired, or lacks the plan. Every endpoint fails identically. */
    CREDENTIALS(false, false),

    /**
     * 402. Out of credits. Retrying spends nothing but time, and moving to another endpoint spends
     * credits we do not have — so this stops everything and wants a human.
     */
    QUOTA_EXHAUSTED(false, false),

    /** 400/422. The request is malformed, which makes it our bug; the next endpoint rejects it too. */
    BAD_REQUEST(false, false),

    /**
     * 404, or a 200 carrying no rows. Not a failure at all but an <i>answer</i> — "nobody matched
     * that filter" — and therefore the one kind that moves a cascade on to its next attempt.
     */
    NOT_FOUND(false, true),

    /**
     * 429. Waiting is the fix, so it is retried; changing endpoint is not, so it does not advance a
     * cascade. A 429 on the first attempt is evidence the second would be throttled too, and
     * advancing would turn one burst into a multiple of itself, all of it billable.
     */
    RATE_LIMITED(true, false),

    /** 5xx. Their bad minute. */
    UNAVAILABLE(true, false),

    /**
     * A connect or read timeout, or a dropped connection. Retryable only for an idempotent call: a
     * write that timed out may already have been processed, and retrying it charges us twice.
     */
    TIMEOUT(true, false),

    /**
     * A 2xx body that would not deserialise — their contract changed, or something in front of them
     * answered with an HTML error page. Re-reading gets the same broken body.
     */
    MALFORMED_RESPONSE(false, false);

    private final boolean retryable;
    private final boolean advancesCascade;

    VendorFailureKind(boolean retryable, boolean advancesCascade) {
        this.retryable = retryable;
        this.advancesCascade = advancesCascade;
    }

    /**
     * Whether trying the identical call again could plausibly succeed. The base truth only —
     * {@code VendorFailure.worthRetrying} applies the two refinements that need request context.
     */
    public boolean retryable() {
        return retryable;
    }

    /** Whether a cascade should fall through to its next attempt rather than give up here. */
    public boolean advancesCascade() {
        return advancesCascade;
    }

    /** The vendor answered, and the status says what it thought of the request. */
    public static VendorFailureKind of(HttpStatusCode status) {
        int code = status.value();
        return switch (code) {
            case 401, 403 -> CREDENTIALS;
            case 402 -> QUOTA_EXHAUSTED;
            case 404 -> NOT_FOUND;
            case 429 -> RATE_LIMITED;
            default -> status.is5xxServerError() ? UNAVAILABLE : BAD_REQUEST;
        };
    }

    /**
     * The vendor did not answer, or answered something unreadable — neither of which ever reaches a
     * status handler, because in both cases there is no status to hand it.
     *
     * <p>{@code RestClient} raises {@link ResourceAccessException} for a transport failure before any
     * response exists, and wraps a conversion failure as a plain {@link RestClientException}. A layer
     * that classified only from status codes would let both through as raw Spring exceptions,
     * unclassified and therefore un-retried.
     */
    public static VendorFailureKind of(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpConnectTimeoutException || cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException) {
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
