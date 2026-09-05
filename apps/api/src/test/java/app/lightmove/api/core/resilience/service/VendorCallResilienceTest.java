package app.lightmove.api.core.resilience.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ResilienceSettings;
import app.lightmove.api.core.logging.service.CorrelationId;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import app.lightmove.api.core.resilience.model.VendorException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * What the vendor layer is for: paying twice only when paying twice could work.
 *
 * <p>The retry lives on a Spring proxy and the classification lives in a status handler, so neither
 * is proved by calling the pieces directly — this drives a {@code @Retryable} bean through the real
 * client and lets {@code MockRestServiceServer} count the requests that actually left. The count is
 * the assertion that matters: it is the difference between a policy and a comment describing one.
 *
 * <p>The client is built through the factory's package-private transport overload, which is the only
 * way to get Spring's mock transport past a factory that otherwise always installs its own.
 */
@SpringJUnitConfig(VendorCallResilienceTest.Config.class)
@TestPropertySource(properties = {
        "lightmove.resilience.max-retries=2",
        "lightmove.resilience.retry-delay=10ms",
        "lightmove.resilience.retry-jitter=0",
        "lightmove.resilience.retry-multiplier=1.0",
        "lightmove.resilience.retry-max-delay=50ms"
})
class VendorCallResilienceTest {

    private static final String LOOKUP_URL = "https://vendor.example/lookup";

    @Autowired private TestVendorClient client;
    @Autowired private VendorClientFactory clientFactory;
    @Autowired private VendorRateLimiter rateLimiter;

    private MockRestServiceServer vendor;

    @BeforeEach
    void bindVendor() {
        RestClient.Builder builder = RestClient.builder();
        vendor = MockRestServiceServer.bindTo(builder).build();
        client.pointAt(builder, clientFactory, rateLimiter);
    }

    @Test
    @DisplayName("a rate limit is waited out: a second request is made and answers")
    void aRateLimitIsRetried() {
        vendor.expect(once(), requestTo(LOOKUP_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        vendor.expect(once(), requestTo(LOOKUP_URL))
                .andRespond(withSuccess("{\"name\":\"SampleCo\"}", MediaType.APPLICATION_JSON));

        assertThat(client.lookup()).containsEntry("name", "SampleCo");
        vendor.verify();
    }

    @Test
    @DisplayName("a dropped connection is retried — the failure with no status at all")
    void aTransportFailureIsRetried() {
        // The path a status-only classifier would miss entirely, and the most ordinary reason to
        // try again. RestClient raises this before any response exists.
        vendor.expect(once(), requestTo(LOOKUP_URL))
                .andRespond(withException(new SocketTimeoutException("read timed out")));
        vendor.expect(once(), requestTo(LOOKUP_URL))
                .andRespond(withSuccess("{\"name\":\"SampleCo\"}", MediaType.APPLICATION_JSON));

        assertThat(client.lookup()).containsEntry("name", "SampleCo");
        vendor.verify();
    }

    @Test
    @DisplayName("an outage is retried to the configured ceiling and no further")
    void anOutageIsRetriedAndThenGivesUp() {
        // maxRetries=2 means the first attempt plus two more, never an unbounded loop.
        vendor.expect(times(3), requestTo(LOOKUP_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(client::lookup)
                .isInstanceOf(VendorException.class)
                .extracting(failure -> ((VendorException) failure).getKind())
                .isEqualTo(VendorFailureKind.UNAVAILABLE);
        vendor.verify();
    }

    @Test
    @DisplayName("a bad key is not paid for three times")
    void credentialsFailOnTheFirstAttempt() {
        vendor.expect(once(), requestTo(LOOKUP_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(client::lookup)
                .isInstanceOf(VendorException.class)
                .extracting(failure -> ((VendorException) failure).getKind())
                .isEqualTo(VendorFailureKind.CREDENTIALS);
        vendor.verify();
    }

    @Test
    @DisplayName("running out of credits stops rather than spending the time to be told twice")
    void quotaExhaustedFailsOnTheFirstAttempt() {
        vendor.expect(once(), requestTo(LOOKUP_URL)).andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED));

        assertThatThrownBy(client::lookup)
                .isInstanceOf(VendorException.class)
                .extracting(failure -> ((VendorException) failure).getKind())
                .isEqualTo(VendorFailureKind.QUOTA_EXHAUSTED);
        vendor.verify();
    }

    @Test
    @DisplayName("every request carries the key and the correlation id")
    void theRequestCarriesItsHeaders() {
        vendor.expect(once(), requestTo(LOOKUP_URL))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(header(CorrelationId.HEADER, CorrelationId.current()))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.lookup();
        vendor.verify();
    }

    @Test
    @DisplayName("a vendor's error body never reaches the exception a log would carry")
    void theErrorBodyIsNotInTheMessage() {
        // The body echoes the query, so on a real lookup this is the researched person's name.
        vendor.expect(once(), requestTo(LOOKUP_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .body("{\"error\":\"no match for Jane Executive at RetailCo\"}")
                .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(client::lookup)
                .isInstanceOf(VendorException.class)
                .hasMessageNotContaining("Jane Executive")
                .hasMessageNotContaining("RetailCo");
    }

    /**
     * A stand-in adapter carrying what every real one does: the annotation, the guard, and nothing
     * else. {@code lookup} is only ever called from outside, because a call through {@code this}
     * would bypass the proxy and quietly test no retry at all.
     */
    static class TestVendorClient {

        private final VendorCallGuard guard;
        private RestClient client;

        TestVendorClient(VendorCallGuard guard) {
            this.guard = guard;
        }

        void pointAt(RestClient.Builder builder, VendorClientFactory clientFactory,
                     VendorRateLimiter rateLimiter) {
            this.client = clientFactory.createKeepingTransport(
                    VendorClientSpec.bearer("stub", "https://vendor.example", "test-key",
                            Duration.ofSeconds(5), 100),
                    builder, rateLimiter);
        }

        @Retryable(
                predicate = VendorRetryPredicate.class,
                maxRetriesString = "${lightmove.resilience.max-retries}",
                delayString = "${lightmove.resilience.retry-delay}",
                jitterString = "${lightmove.resilience.retry-jitter}",
                multiplierString = "${lightmove.resilience.retry-multiplier}",
                maxDelayString = "${lightmove.resilience.retry-max-delay}")
        @SuppressWarnings("unchecked")
        Map<String, Object> lookup() {
            return guard.call(VendorCall.of("stub", "lookup"), () -> client.get()
                    .uri("/lookup")
                    .retrieve()
                    .body(Map.class));
        }
    }

    @Configuration
    @EnableResilientMethods
    static class Config {

        @Bean
        LightMoveProperties lightMoveProperties() {
            ResilienceSettings resilience = new ResilienceSettings(Duration.ofSeconds(5), 2,
                    Duration.ofMillis(10), 1.0, Duration.ZERO, Duration.ofMillis(50),
                    Duration.ofSeconds(2));
            return new LightMoveProperties(null, null, null, null, null, null, null, resilience, null);
        }

        @Bean
        VendorRateLimiter vendorRateLimiter() {
            return new VendorRateLimiter();
        }

        @Bean
        VendorClientFactory vendorClientFactory(LightMoveProperties properties) {
            return new VendorClientFactory(properties);
        }

        @Bean
        VendorCallGuard vendorCallGuard(VendorRateLimiter rateLimiter, LightMoveProperties properties) {
            return new VendorCallGuard(rateLimiter, properties);
        }

        @Bean
        TestVendorClient testVendorClient(VendorCallGuard guard) {
            return new TestVendorClient(guard);
        }
    }
}
