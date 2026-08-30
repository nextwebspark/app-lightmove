package app.lightmove.api.core.vendor.constant;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.vendor.model.VendorCall;
import app.lightmove.api.core.vendor.model.VendorFailure;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/**
 * Reading what a vendor's failure means, without a vendor.
 *
 * <p>This half is worth testing exhaustively because it is the half every other decision is made
 * from, and because each row of it is a spending decision: whether we pay for the same call again,
 * and whether we pay for a different one. A wrong cell here is not a wrong answer, it is an invoice.
 */
class VendorFailureKindTest {

    private static final Duration CEILING = Duration.ofSeconds(2);
    private static final VendorCall READ = VendorCall.read("acme", "search");
    private static final VendorCall WRITE = VendorCall.write("acme", "enrich");

    @Test
    @DisplayName("a rejected key is neither retried nor routed around")
    void credentialsAreTerminal() {
        VendorFailureKind kind = VendorFailureKind.of(HttpStatus.UNAUTHORIZED);

        assertThat(kind).isEqualTo(VendorFailureKind.CREDENTIALS);
        assertThat(VendorFailure.of(kind).worthRetrying(READ, CEILING)).isFalse();
        assertThat(kind.advancesCascade()).isFalse();
    }

    @Test
    @DisplayName("an empty credit balance stops everything — another endpoint costs credits too")
    void quotaExhaustedStopsTheCascade() {
        VendorFailureKind kind = VendorFailureKind.of(HttpStatus.PAYMENT_REQUIRED);

        assertThat(kind).isEqualTo(VendorFailureKind.QUOTA_EXHAUSTED);
        assertThat(VendorFailure.of(kind).worthRetrying(READ, CEILING)).isFalse();
        assertThat(kind.advancesCascade()).isFalse();
    }

    @Test
    @DisplayName("a malformed request is our bug, so the next endpoint would reject it identically")
    void badRequestStopsTheCascade() {
        assertThat(VendorFailureKind.of(HttpStatus.UNPROCESSABLE_ENTITY)).isEqualTo(VendorFailureKind.BAD_REQUEST);
        assertThat(VendorFailureKind.of(HttpStatus.BAD_REQUEST)).isEqualTo(VendorFailureKind.BAD_REQUEST);
        assertThat(VendorFailureKind.BAD_REQUEST.advancesCascade()).isFalse();
        assertThat(VendorFailure.of(VendorFailureKind.BAD_REQUEST).worthRetrying(READ, CEILING)).isFalse();
    }

    @Test
    @DisplayName("not found is the only kind that moves a cascade on — it is an answer, not a failure")
    void notFoundIsTheOnlyKindThatAdvances() {
        assertThat(VendorFailureKind.of(HttpStatus.NOT_FOUND)).isEqualTo(VendorFailureKind.NOT_FOUND);

        for (VendorFailureKind kind : VendorFailureKind.values()) {
            assertThat(kind.advancesCascade())
                    .as("%s advances the cascade", kind)
                    .isEqualTo(kind == VendorFailureKind.NOT_FOUND);
        }
    }

    @Test
    @DisplayName("being throttled is retried but never routed around")
    void rateLimitedIsRetriedInPlace() {
        VendorFailureKind kind = VendorFailureKind.of(HttpStatus.TOO_MANY_REQUESTS);

        assertThat(kind).isEqualTo(VendorFailureKind.RATE_LIMITED);
        assertThat(VendorFailure.of(kind).worthRetrying(READ, CEILING)).isTrue();
        // Moving to the next endpoint while throttled would turn one burst into a multiple of itself.
        assertThat(kind.advancesCascade()).isFalse();
    }

    @Test
    @DisplayName("a Retry-After we are unwilling to wait out makes the failure permanent")
    void aLongRetryAfterStopsTheRetry() {
        VendorFailure soon = new VendorFailure(VendorFailureKind.RATE_LIMITED, 429, Duration.ofSeconds(1), null);
        VendorFailure later = new VendorFailure(VendorFailureKind.RATE_LIMITED, 429, Duration.ofMinutes(5), null);

        assertThat(soon.worthRetrying(READ, CEILING)).isTrue();
        // Spring's backoff cannot read the header, so honouring "come back in five minutes" by
        // sleeping would park a request thread for five minutes. Stopping is the honest answer.
        assertThat(later.worthRetrying(READ, CEILING)).isFalse();
    }

    @Test
    @DisplayName("every 5xx is their bad minute and worth trying again")
    void serverErrorsAreRetried() {
        assertThat(VendorFailureKind.of(HttpStatus.INTERNAL_SERVER_ERROR)).isEqualTo(VendorFailureKind.UNAVAILABLE);
        assertThat(VendorFailureKind.of(HttpStatus.BAD_GATEWAY)).isEqualTo(VendorFailureKind.UNAVAILABLE);
        assertThat(VendorFailure.of(VendorFailureKind.UNAVAILABLE).worthRetrying(READ, CEILING)).isTrue();
    }

    @Test
    @DisplayName("a timeout is retried for a read and never for a write")
    void timeoutRetryDependsOnIdempotence() {
        VendorFailure timedOut = VendorFailure.of(VendorFailureKind.TIMEOUT);

        assertThat(timedOut.worthRetrying(READ, CEILING)).isTrue();
        // The call may already have been processed, and charged, before the socket gave up.
        assertThat(timedOut.worthRetrying(WRITE, CEILING)).isFalse();
    }

    @Test
    @DisplayName("a transport failure classifies as a timeout, though it carries no status at all")
    void transportFailuresAreClassifiedFromTheException() {
        Throwable dropped = new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out"));

        // This is the case a status handler can never see: there is no response to read a status from.
        assertThat(VendorFailureKind.of(dropped)).isEqualTo(VendorFailureKind.TIMEOUT);
        assertThat(VendorFailureKind.of(new ResourceAccessException("reset", new IOException("Connection reset"))))
                .isEqualTo(VendorFailureKind.TIMEOUT);
    }

    @Test
    @DisplayName("a body that will not deserialise is not worth asking for a second time")
    void unreadableBodiesAreNotRetried() {
        Throwable unreadable = new RestClientException("Error while extracting response for type [Foo]");

        assertThat(VendorFailureKind.of(unreadable)).isEqualTo(VendorFailureKind.MALFORMED_RESPONSE);
        assertThat(VendorFailure.of(VendorFailureKind.MALFORMED_RESPONSE).worthRetrying(READ, CEILING)).isFalse();
    }

    @Test
    @DisplayName("a log line names the classification and never the body")
    void describeCarriesNoPayload() {
        VendorFailure failure = new VendorFailure(
                VendorFailureKind.BAD_REQUEST, 422, null, "{\"query\":\"Sara Al-Mansouri\"}");

        assertThat(failure.describe()).isEqualTo("BAD_REQUEST (HTTP 422)");
        assertThat(failure.describe()).doesNotContain("Sara");
    }
}
