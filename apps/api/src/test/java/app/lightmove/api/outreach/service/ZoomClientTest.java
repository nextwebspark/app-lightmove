package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ResilienceSettings;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.ZoomMeeting;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Zoom against recorded answers: the consent link, the account read, a meeting made and deleted, and a revoke. */
class ZoomClientTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ProviderCredentials APP = new ProviderCredentials(IntegrationProvider.ZOOM,
            CredentialMode.SHARED, "uncava-zoom-client", "uncava-zoom-secret", null);
    private static final URI CALLBACK = URI.create("https://beta.uncava.com/api/v1/outreach/zoom/callback");

    private final List<String> requested = new CopyOnWriteArrayList<>();
    private final Map<String, String> bodies = new ConcurrentHashMap<>();
    private final Map<String, String> authorizations = new ConcurrentHashMap<>();
    private volatile int createStatus = 201;

    private HttpServer server;
    private ZoomClient zoom;

    @BeforeEach
    void startRecordedZoom() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::answer);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        LightMoveProperties properties = mock(LightMoveProperties.class);
        when(properties.resilience()).thenReturn(new ResilienceSettings(Duration.ofSeconds(2), 0,
                Duration.ofMillis(1), 1.0, Duration.ZERO, Duration.ofMillis(1), Duration.ofSeconds(1)));
        VendorRateLimiter limiter = new VendorRateLimiter();
        zoom = new ZoomClient(new VendorClientFactory(properties), limiter, new VendorCallGuard(limiter, properties),
                base + "/api", base + "/oauth-host");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    @DisplayName("the consent link names the app, the redirect and the state, and never the secret")
    void theConsentLink() {
        URI link = zoom.authorizationUri(APP, "state-1", CALLBACK);
        MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(link).build().getQueryParams();

        assertThat(link.getPath()).isEqualTo("/oauth-host/oauth/authorize");
        assertThat(query.getFirst("response_type")).isEqualTo("code");
        assertThat(query.getFirst("client_id")).isEqualTo("uncava-zoom-client");
        assertThat(query.getFirst("state")).isEqualTo("state-1");
        assertThat(link.toString()).doesNotContain("uncava-zoom-secret");
    }

    @Test
    @DisplayName("a meeting is scheduled at the call's time and length, answering its id and join link")
    void aMeetingIsScheduled() throws Exception {
        assertThat(zoom.userIdOf("zoom-access")).isEqualTo("KDcuGIm1QgePTO8WbOqwIQ");

        ZoomMeeting meeting = zoom.createMeeting("zoom-access", "Confidential: first conversation",
                Instant.parse("2026-10-06T06:00:00.123Z"), 30);

        assertThat(meeting.id()).isEqualTo("85746065432");
        assertThat(meeting.joinUrl()).isEqualTo("https://us05web.zoom.us/j/85746065432?pwd=abc123");
        JsonNode body = JSON.readTree(bodies.get("POST /api/v2/users/me/meetings"));
        assertThat(body.path("type").asInt()).isEqualTo(2);
        assertThat(body.path("start_time").asString("")).isEqualTo("2026-10-06T06:00:00Z");
        assertThat(body.path("duration").asInt()).isEqualTo(30);
        assertThat(body.path("timezone").asString("")).isEqualTo("UTC");
        assertThat(authorizations.get("/api/v2/users/me/meetings")).isEqualTo("Bearer zoom-access");

        zoom.deleteMeeting("zoom-access", meeting.id());
        assertThat(requested).contains("DELETE /api/v2/meetings/85746065432");
    }

    @Test
    @DisplayName("a refused create is never tried again")
    void aRefusedCreateIsTriedOnce() {
        createStatus = 429;

        assertThatThrownBy(() -> zoom.createMeeting("zoom-access", "Call", Instant.parse("2026-10-06T06:00:00Z"), 30))
                .isInstanceOfSatisfying(VendorException.class,
                        failed -> assertThat(failed.getKind()).isEqualTo(VendorFailureKind.RATE_LIMITED));
        assertThat(requested).filteredOn("POST /api/v2/users/me/meetings"::equals).hasSize(1);
    }

    @Test
    @DisplayName("a revoke sends the token with the app's own Basic credentials")
    void aRevokeAuthenticatesTheApp() {
        zoom.revoke(APP, "zoom-refresh");

        assertThat(bodies.get("POST /oauth-host/oauth/revoke")).isEqualTo("token=zoom-refresh");
        assertThat(authorizations.get("/oauth-host/oauth/revoke")).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString("uncava-zoom-client:uncava-zoom-secret".getBytes(StandardCharsets.UTF_8)));
    }

    private void answer(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String key = exchange.getRequestMethod() + " " + path;
        requested.add(key);
        bodies.put(key, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        authorizations.put(path, String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
        switch (key) {
            case "GET /api/v2/users/me" -> respond(exchange, 200, """
                    {"id":"KDcuGIm1QgePTO8WbOqwIQ","first_name":"Yara","last_name":"Haddad","type":2,
                     "email":"yara.haddad@meridian.example"}""");
            case "POST /api/v2/users/me/meetings" -> respond(exchange, createStatus, createStatus != 201
                    ? "{\"code\":429,\"message\":\"Too many requests\"}" : """
                    {"id":85746065432,"uuid":"aDYlohsHRtCd4ii1uC2+hA==","topic":"Confidential: first conversation",
                     "type":2,"start_time":"2026-10-06T06:00:00Z","duration":30,"timezone":"UTC",
                     "join_url":"https://us05web.zoom.us/j/85746065432?pwd=abc123",
                     "start_url":"https://us05web.zoom.us/s/85746065432?zak=secret"}""");
            case "DELETE /api/v2/meetings/85746065432", "POST /oauth-host/oauth/revoke" -> respond(exchange, 204, "");
            default -> respond(exchange, 404, "{\"code\":404}");
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}
