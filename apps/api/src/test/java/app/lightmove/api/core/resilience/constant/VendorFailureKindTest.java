package app.lightmove.api.core.resilience.constant;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/**
 * The decision that spends money: which failures are worth paying to repeat. Reading a vendor's
 * answer is the half worth testing exhaustively, so both mappings are pure statics rather than logic
 * buried in an HTTP handler.
 */
class VendorFailureKindTest {

    @Test
    @DisplayName("a status the vendor answered with maps to what it means for us")
    void aStatusMaps() {
        assertThat(VendorFailureKind.of(HttpStatus.UNAUTHORIZED)).isEqualTo(VendorFailureKind.CREDENTIALS);
        assertThat(VendorFailureKind.of(HttpStatus.FORBIDDEN)).isEqualTo(VendorFailureKind.CREDENTIALS);
        assertThat(VendorFailureKind.of(HttpStatus.PAYMENT_REQUIRED)).isEqualTo(VendorFailureKind.QUOTA_EXHAUSTED);
        assertThat(VendorFailureKind.of(HttpStatus.NOT_FOUND)).isEqualTo(VendorFailureKind.NOT_FOUND);
        assertThat(VendorFailureKind.of(HttpStatus.TOO_MANY_REQUESTS)).isEqualTo(VendorFailureKind.RATE_LIMITED);
        assertThat(VendorFailureKind.of(HttpStatus.BAD_GATEWAY)).isEqualTo(VendorFailureKind.UNAVAILABLE);
        assertThat(VendorFailureKind.of(HttpStatus.BAD_REQUEST)).isEqualTo(VendorFailureKind.BAD_REQUEST);
        assertThat(VendorFailureKind.of(HttpStatus.UNPROCESSABLE_CONTENT)).isEqualTo(VendorFailureKind.BAD_REQUEST);
    }

    @Test
    @DisplayName("a failure with no status at all is still classified, not left to fall through")
    void aTransportFailureMaps() {
        // The one that matters: RestClient raises these before any response exists, so a classifier
        // that only read status codes would leave a dropped connection un-retried.
        assertThat(VendorFailureKind.of(new ResourceAccessException("connection reset")))
                .isEqualTo(VendorFailureKind.TIMEOUT);
        assertThat(VendorFailureKind.of(new RestClientException("wrapped",
                new SocketTimeoutException("read timed out"))))
                .isEqualTo(VendorFailureKind.TIMEOUT);
        assertThat(VendorFailureKind.of(new RestClientException("wrapped",
                new HttpConnectTimeoutException("connect timed out"))))
                .isEqualTo(VendorFailureKind.TIMEOUT);
        // Something we cannot read is not something re-reading fixes.
        assertThat(VendorFailureKind.of(new IllegalStateException("no converter")))
                .isEqualTo(VendorFailureKind.MALFORMED_RESPONSE);
    }

    @Test
    @DisplayName("only a wait, an outage or a timeout is worth paying for twice")
    void onlyTheTransientKindsRetry() {
        assertThat(VendorFailureKind.RATE_LIMITED.retryable()).isTrue();
        assertThat(VendorFailureKind.UNAVAILABLE.retryable()).isTrue();
        assertThat(VendorFailureKind.TIMEOUT.retryable()).isTrue();

        assertThat(VendorFailureKind.CREDENTIALS.retryable()).isFalse();
        assertThat(VendorFailureKind.QUOTA_EXHAUSTED.retryable()).isFalse();
        assertThat(VendorFailureKind.BAD_REQUEST.retryable()).isFalse();
        assertThat(VendorFailureKind.NOT_FOUND.retryable()).isFalse();
        assertThat(VendorFailureKind.MALFORMED_RESPONSE.retryable()).isFalse();
    }
}
