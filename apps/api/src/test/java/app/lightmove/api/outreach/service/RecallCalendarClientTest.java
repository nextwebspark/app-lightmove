package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.RecallSettings;
import app.lightmove.api.core.config.ResilienceSettings;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.outreach.constant.RecallCalendarStatus;
import app.lightmove.api.outreach.model.RecallCalendarEvent;
import app.lightmove.api.outreach.model.RecallWebhookDelivery;
import app.lightmove.api.outreach.model.RecallWebhookNotice;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recall's Svix-signed webhook, the calendar answers it gives and its changed-event list, read against their
 * documented shapes.
 */
class RecallCalendarClientTest {

    private static final String KEY = Base64.getEncoder().encodeToString("recall-signing-key-32-bytes-long".getBytes(
            StandardCharsets.UTF_8));
    private static final String SECRET = "whsec_" + KEY;
    private static final Instant NOW = Instant.parse("2026-10-02T09:00:00Z");
    private static final byte[] BODY = """
            {"event":"calendar.update","data":{"calendar_id":"cal-123"}}""".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("a delivery signed with the endpoint's secret verifies, among several signatures")
    void aSignedDeliveryVerifies() throws Exception {
        String timestamp = String.valueOf(NOW.getEpochSecond());
        RecallWebhookDelivery delivery = new RecallWebhookDelivery("msg_1", timestamp,
                "v1,bm90LXRoaXMtb25l v1," + sign("msg_1", timestamp, BODY), BODY);

        assertThat(RecallCalendarClient.verifies(SECRET, delivery, NOW)).isTrue();
        assertThat(RecallCalendarClient.noticesIn(BODY))
                .containsExactly(new RecallWebhookNotice.CalendarStateChanged("cal-123"));
    }

    @Test
    @DisplayName("a forged, altered, stale or unsigned delivery, or a blank secret, is refused")
    void anythingElseIsRefused() throws Exception {
        String timestamp = String.valueOf(NOW.getEpochSecond());
        String signature = "v1," + sign("msg_1", timestamp, BODY);
        byte[] altered = "{\"event\":\"calendar.update\",\"data\":{\"calendar_id\":\"cal-999\"}}"
                .getBytes(StandardCharsets.UTF_8);
        String stale = String.valueOf(NOW.minus(Duration.ofMinutes(6)).getEpochSecond());

        assertThat(RecallCalendarClient.verifies(SECRET,
                new RecallWebhookDelivery("msg_1", timestamp, "v1,Zm9yZ2Vk", BODY), NOW)).isFalse();
        assertThat(RecallCalendarClient.verifies(SECRET,
                new RecallWebhookDelivery("msg_1", timestamp, signature, altered), NOW)).isFalse();
        assertThat(RecallCalendarClient.verifies(SECRET,
                new RecallWebhookDelivery("msg_1", stale, "v1," + sign("msg_1", stale, BODY), BODY), NOW)).isFalse();
        assertThat(RecallCalendarClient.verifies(SECRET,
                new RecallWebhookDelivery("msg_1", timestamp, null, BODY), NOW)).isFalse();
        assertThat(RecallCalendarClient.verifies("",
                new RecallWebhookDelivery("msg_1", timestamp, signature, BODY), NOW)).isFalse();
    }

    @Test
    @DisplayName("calendar.update and calendar.sync_events name their calendar; anything else names nothing")
    void eventsAndStatuses() {
        assertThat(RecallCalendarClient.noticesIn("""
                {"event":"calendar.sync_events","data":{"calendar_id":"cal-123",
                 "last_updated_ts":"2026-10-02T08:59:30.123456+00:00"}}""".getBytes(StandardCharsets.UTF_8)))
                .containsExactly(new RecallWebhookNotice.CalendarEventsChanged("cal-123",
                        Instant.parse("2026-10-02T08:59:30.123456Z")));
        assertThat(RecallCalendarClient.noticesIn("""
                {"event":"calendar.sync_events","data":{"calendar_id":"cal-123"}}""".getBytes(StandardCharsets.UTF_8)))
                .isEmpty();
        assertThat(RecallCalendarClient.noticesIn("""
                {"event":"bot.done","data":{"calendar_id":"cal-123"}}""".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(RecallCalendarClient.noticesIn("not json".getBytes(StandardCharsets.UTF_8))).isEmpty();

        JsonMapper json = JsonMapper.builder().build();
        assertThat(RecallCalendarClient.statusOf(json.readTree("\"disconnected\"")))
                .isEqualTo(RecallCalendarStatus.DISCONNECTED);
        assertThat(RecallCalendarClient.statusOf(json.readTree("\"connected\"")))
                .isEqualTo(RecallCalendarStatus.CONNECTED);
        assertThat(RecallCalendarClient.statusOf(json.readTree("\"connecting\"")))
                .isEqualTo(RecallCalendarStatus.CONNECTING);
    }

    @Test
    @DisplayName("changed events are listed page by page, taking only the cursor of Recall's next link")
    void changedEventsArePaged() throws IOException {
        List<String> queries = new CopyOnWriteArrayList<>();
        List<String> keys = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String query = URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            queries.add(exchange.getRequestURI().getPath() + "?" + query);
            keys.add(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, query.contains("cursor=") ? """
                    {"next":null,"results":[{"id":"r2","platform":"microsoft_outlook","platform_id":"AAMk2",
                     "ical_uid":"040000008200E0","is_deleted":true,"raw":null}]}""" : """
                    {"next":"https://evil.example/api/v2/calendar-events/?cursor=cD0yMDI2%2B1&calendar_id=other",
                     "results":[{"id":"r1","platform":"google_calendar","platform_id":"evt-1","ical_uid":"evt-1@google",
                     "is_deleted":false,"raw":{"id":"evt-1","status":"confirmed"}}]}""");
        });
        server.start();
        try {
            LightMoveProperties properties = mock(LightMoveProperties.class);
            when(properties.resilience()).thenReturn(new ResilienceSettings(Duration.ofSeconds(2), 0,
                    Duration.ofMillis(1), 1.0, Duration.ZERO, Duration.ofMillis(1), Duration.ofSeconds(1)));
            VendorRateLimiter limiter = new VendorRateLimiter();
            RecallCalendarClient client = new RecallCalendarClient(
                    new RecallSettings("recall-key", SECRET, "http://127.0.0.1:" + server.getAddress().getPort()),
                    new VendorClientFactory(properties), limiter, new VendorCallGuard(limiter, properties),
                    RestClient.builder(), Clock.fixed(NOW, ZoneOffset.UTC));

            List<RecallCalendarEvent> events = client.eventsUpdatedSince("cal-123",
                    Instant.parse("2026-10-02T08:59:30Z"));

            assertThat(events).extracting(RecallCalendarEvent::platformId).containsExactly("evt-1", "AAMk2");
            assertThat(events.get(1).deleted()).isTrue();
            assertThat(events.get(0).raw().path("id").asText()).isEqualTo("evt-1");
            assertThat(queries).hasSize(2);
            assertThat(queries.get(0)).startsWith("/api/v2/calendar-events/?")
                    .contains("calendar_id=cal-123").contains("updated_at__gte=2026-10-02T08:59:30Z");
            assertThat(queries.get(1)).contains("calendar_id=cal-123").contains("cursor=cD0yMDI2+1");
            assertThat(keys).containsOnly("Token recall-key");
        } finally {
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String sign(String id, String timestamp, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(KEY), "HmacSHA256"));
        mac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(mac.doFinal(body));
    }
}
