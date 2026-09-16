package app.lightmove.api.enrichment.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.IntegrationTest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * How many times a failing dataset search is paid for: once. One timeout's worth of patience is
 * already the worst case, and the HarvestAPI fallback is a better second attempt than the same query
 * asked again of a vendor that just failed to answer it.
 *
 * <p><b>A retry budget here is not what it looks like.</b> Spring 7.0.8's retry interceptor re-enters
 * itself through {@code ProxyMethodInvocation.invocableClone()}, so attempts come out as
 * <i>(1 + maxRetries)²</i> — measured here at 1, 4 and 9 requests for budgets of 0, 1 and 2. A single
 * retry is therefore four timeouts of waiting rather than two, which is why this one is zero.
 *
 * <p>The count is what has to be asserted, and only a real request can be counted: the vendor client
 * factory always installs its own transport, so this points the adapter at a local server answering
 * 503 rather than at a mocked one. It is also the only test that proves
 * {@code brightdata.max-retries} is spelled in {@code application.yml} — {@code @Retryable} resolves
 * it against the environment, which never sees the settings record's binding default, and a
 * placeholder that resolved to nothing would fall back to the annotation's own default of 3 and
 * announce itself here as sixteen requests.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "lightmove.enrichment.provider=brightdata",
        "lightmove.enrichment.brightdata.api-key=test-dataset-key"
})
class BrightDataRetryBudgetTest {

    private static final AtomicInteger REQUESTS = new AtomicInteger();
    private static final HttpServer VENDOR = unavailableVendor();

    @Autowired
    @Qualifier("brightDataProfileEnricher")
    private BrightDataProfileEnricher dataset;

    @DynamicPropertySource
    static void pointAtTheStubVendor(DynamicPropertyRegistry registry) {
        registry.add("lightmove.enrichment.brightdata.base-url",
                () -> "http://127.0.0.1:" + VENDOR.getAddress().getPort());
    }

    @BeforeEach
    void resetTheCount() {
        REQUESTS.set(0);
    }

    @Test
    @DisplayName("a failing dataset search is paid for once, then handed to the fallback")
    void aFailingSearchIsPaidForOnce() {
        assertThatThrownBy(() -> dataset.fetch("https://www.linkedin.com/in/sample-profile/"))
                .hasMessageContaining("brightdata/profile-search failed");

        assertThat(REQUESTS.get()).isEqualTo(1);
    }

    private static HttpServer unavailableVendor() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                REQUESTS.incrementAndGet();
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException cannotBind) {
            throw new IllegalStateException(cannotBind);
        }
    }
}
