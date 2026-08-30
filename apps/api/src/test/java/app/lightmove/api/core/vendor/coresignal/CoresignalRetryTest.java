package app.lightmove.api.core.vendor.coresignal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ResilientMethodsConfig;
import app.lightmove.api.core.vendor.StubVendorServer;
import app.lightmove.api.core.vendor.constant.VendorFailureKind;
import app.lightmove.api.core.vendor.coresignal.config.CoresignalConfig;
import app.lightmove.api.core.vendor.coresignal.service.CoresignalEmployeeClient;
import app.lightmove.api.core.vendor.model.VendorException;
import app.lightmove.api.core.vendor.service.VendorCallGuard;
import app.lightmove.api.core.vendor.service.VendorClientFactory;
import app.lightmove.api.core.vendor.service.VendorRateLimiter;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/**
 * Proves the retry is real, which nothing else can.
 *
 * <p>{@code @Retryable} is an annotation on a method: it does nothing until Spring has created a
 * proxy around the bean and {@code @EnableResilientMethods} has registered the advice that builds
 * one. Both are easy to lose and neither fails loudly — the calls simply stop being retried, and the
 * first anyone hears of it is a vendor's bad minute becoming a failed mandate. So this test boots a
 * context, and every assertion is a request count against a real server.
 *
 * <p>It also pins the split between {@code CoresignalEmployeeClient} and
 * {@code CoresignalEmployeeSearch}. Fold the cascade back into the client and its calls become
 * self-invocations that bypass the proxy; these counts are what would catch that.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {
                ResilientMethodsConfig.class,
                CoresignalConfig.class,
                VendorClientFactory.class,
                VendorCallGuard.class,
                VendorRateLimiter.class,
                CoresignalRetryTest.VendorTestContext.class
        },
        properties = {
                "lightmove.vendor.connect-timeout=2s",
                "lightmove.vendor.read-timeout=2s",
                "lightmove.vendor.max-retries=2",
                "lightmove.vendor.retry-delay=5ms",
                "lightmove.vendor.retry-jitter=1ms",
                "lightmove.vendor.retry-multiplier=1.0",
                "lightmove.vendor.retry-max-delay=20ms",
                "lightmove.vendor.retry-after-ceiling=2s",
                "lightmove.vendor.permit-max-wait=2s",
                "lightmove.vendor.coresignal.enabled=true",
                "lightmove.vendor.coresignal.api-key=test-key",
                // Unpaced: this test is about retries, and a bucket would only add wall-clock to it.
                "lightmove.vendor.coresignal.requests-per-second=0"
        })
class CoresignalRetryTest {

    private static final StubVendorServer CORESIGNAL = new StubVendorServer();

    @Autowired
    private CoresignalEmployeeClient client;

    @DynamicPropertySource
    static void pointAtTheStub(DynamicPropertyRegistry registry) {
        registry.add("lightmove.vendor.coresignal.base-url", CORESIGNAL::baseUrl);
    }

    @BeforeEach
    void clearScript() {
        CORESIGNAL.reset();
    }

    @AfterAll
    static void stopCoresignal() {
        CORESIGNAL.close();
    }

    @Test
    @DisplayName("a rejected key is tried exactly once — the most expensive possible bug in this layer")
    void aBadKeyIsNeverRetried() {
        CORESIGNAL.willAnswer(401, "{\"message\":\"invalid api key\"}");

        assertThatThrownBy(() -> client.atCompanyLinkedInUrl("https://www.linkedin.com/company/acme-gulf"))
                .isInstanceOf(VendorException.class)
                .extracting(failure -> ((VendorException) failure).getFailure().kind())
                .isEqualTo(VendorFailureKind.CREDENTIALS);

        // Retrying a key the vendor has rejected buys nothing and bills three times for it.
        assertThat(CORESIGNAL.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("being throttled is retried, and the second attempt's answer is returned")
    void throttlingIsRetried() {
        CORESIGNAL.willAnswer(429, "{\"message\":\"slow down\"}", Map.of("Retry-After", "0"))
                .willAnswer(200, "[301]");

        var found = client.atCompanyLinkedInUrl("https://www.linkedin.com/company/acme-gulf");

        assertThat(found).isPresent();
        assertThat(CORESIGNAL.requestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("a Retry-After longer than we will wait turns a retryable failure into a final one")
    void aLongRetryAfterIsHonouredByGivingUp() {
        CORESIGNAL.willAnswer(429, "{\"message\":\"slow down\"}", Map.of("Retry-After", "600"));

        assertThatThrownBy(() -> client.atCompanyLinkedInUrl("https://www.linkedin.com/company/acme-gulf"))
                .isInstanceOf(VendorException.class);

        // Ten minutes is longer than a request may hold a thread, and Spring's backoff cannot
        // shorten it, so the honest answer is to stop rather than sleep through it.
        assertThat(CORESIGNAL.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("an outage is retried to the configured limit, then surfaces classified — not as a RetryException")
    void anOutageExhaustsTheRetriesAndSurfacesClassified() {
        CORESIGNAL.willAnswer(503, "{\"message\":\"unavailable\"}");

        assertThatThrownBy(() -> client.atCompanyLinkedInUrl("https://www.linkedin.com/company/acme-gulf"))
                // Asserting the wrapper instead would pass while production answered an opaque 500:
                // GlobalExceptionHandler routes on VendorException and nothing else.
                .isInstanceOf(VendorException.class)
                .extracting(failure -> ((VendorException) failure).getFailure().kind())
                .isEqualTo(VendorFailureKind.UNAVAILABLE);

        assertThat(CORESIGNAL.requestCount()).isEqualTo(3);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LightMoveProperties.class)
    static class VendorTestContext {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }
    }
}
