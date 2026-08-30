package app.lightmove.api.core.vendor.coresignal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.core.config.CoresignalSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.VendorSettings;
import app.lightmove.api.core.vendor.StubVendorServer;
import app.lightmove.api.core.vendor.constant.VendorFailureKind;
import app.lightmove.api.core.vendor.coresignal.model.CoresignalEmployeeReference;
import app.lightmove.api.core.vendor.coresignal.service.CoresignalEmployeeClient;
import app.lightmove.api.core.vendor.coresignal.service.CoresignalEmployeeSearch;
import app.lightmove.api.core.vendor.model.VendorAttemptResult;
import app.lightmove.api.core.vendor.model.VendorException;
import app.lightmove.api.core.vendor.service.VendorCallGuard;
import app.lightmove.api.core.vendor.service.VendorClientFactory;
import app.lightmove.api.core.vendor.service.VendorRateLimiter;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * The cascade against a real socket: ask the precise way, and only then the loose one.
 *
 * <p>No Spring context here, so no retry proxy — that is {@code CoresignalRetryTest}'s job. What this
 * covers is the decision the cascade makes about each answer, and how many calls that costs.
 */
class CoresignalEmployeeSearchTest {

    private StubVendorServer coresignal;
    private CoresignalEmployeeSearch search;

    @BeforeEach
    void startCoresignal() {
        coresignal = new StubVendorServer();

        VendorSettings vendorSettings = new VendorSettings(
                Duration.ofSeconds(2), Duration.ofSeconds(2),
                0, Duration.ofMillis(1), 1.0, Duration.ZERO, Duration.ofMillis(10),
                Duration.ofSeconds(2), Duration.ofSeconds(2), null);

        CoresignalSettings config = new CoresignalSettings(true, "test-key", coresignal.baseUrl(), 0);
        LightMoveProperties properties = new LightMoveProperties(null, null, null, null, null, vendorSettings);

        VendorCallGuard guard = new VendorCallGuard(new VendorRateLimiter(), properties);
        CoresignalEmployeeClient client = new CoresignalEmployeeClient(
                config, vendorSettings, new VendorClientFactory(properties), guard, RestClient.builder());

        search = new CoresignalEmployeeSearch(client);
    }

    @AfterEach
    void stopCoresignal() {
        coresignal.close();
    }

    @Test
    @DisplayName("the LinkedIn URL is asked first, and its answer ends the search")
    void thePreciseLookupWinsWhenItAnswers() {
        coresignal.willAnswer(200, "[101, 102]");

        VendorAttemptResult<List<CoresignalEmployeeReference>> found =
                search.at("https://www.linkedin.com/company/acme-gulf", "acme.com");

        assertThat(found.answeredBy()).isEqualTo("linkedin-url");
        assertThat(found.value()).contains(List.of(
                new CoresignalEmployeeReference(101), new CoresignalEmployeeReference(102)));
        assertThat(coresignal.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("nobody at the LinkedIn URL falls through to the domain, and the result says which answered")
    void anEmptyAnswerFallsThroughToTheDomain() {
        coresignal.willAnswer(200, "[]").willAnswer(200, "[203]");

        VendorAttemptResult<List<CoresignalEmployeeReference>> found =
                search.at("https://www.linkedin.com/company/acme-gulf", "acme.com");

        // Which lookup found them is evidence about the match: a group's domain is weaker than an
        // operating entity's own page, and whatever stores this needs to know that.
        assertThat(found.answeredBy()).isEqualTo("website");
        assertThat(found.value()).contains(List.of(new CoresignalEmployeeReference(203)));
        assertThat(coresignal.requestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("a 404 falls through the same way an empty answer does")
    void notFoundFallsThrough() {
        coresignal.willAnswer(404, "{\"message\":\"not found\"}").willAnswer(200, "[204]");

        VendorAttemptResult<List<CoresignalEmployeeReference>> found =
                search.at("https://www.linkedin.com/company/acme-gulf", "acme.com");

        assertThat(found.answeredBy()).isEqualTo("website");
        assertThat(coresignal.requestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("an empty credit balance stops after one call, not after every lookup in the chain")
    void quotaExhaustedStopsTheChainAtOneCall() {
        coresignal.willAnswer(402, "{\"message\":\"insufficient credits\"}");

        assertThatThrownBy(() -> search.at("https://www.linkedin.com/company/acme-gulf", "acme.com"))
                .isInstanceOf(VendorException.class)
                .extracting(failure -> ((VendorException) failure).getFailure().kind())
                .isEqualTo(VendorFailureKind.QUOTA_EXHAUSTED);

        // The whole reason the cascade branches on the failure kind rather than just moving on.
        assertThat(coresignal.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a company we hold no identifier for costs nothing at all")
    void aCompanyWithNoIdentifiersIsNeverAskedAbout() {
        VendorAttemptResult<List<CoresignalEmployeeReference>> found = search.at(null, "  ");

        assertThat(found.isAnswered()).isFalse();
        assertThat(coresignal.requestCount()).isZero();
    }
}
