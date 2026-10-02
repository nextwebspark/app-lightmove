package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.outreach.constant.RecallCalendarStatus;
import app.lightmove.api.outreach.model.RecallWebhookDelivery;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Recall's Svix-signed webhook and the calendar answers it gives, read against their documented shapes. */
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
        assertThat(RecallCalendarClient.calendarsUpdatedIn(BODY)).containsExactly("cal-123");
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
    @DisplayName("only calendar.update names a calendar; Recall's statuses read as ours")
    void eventsAndStatuses() {
        assertThat(RecallCalendarClient.calendarsUpdatedIn("""
                {"event":"calendar.sync_events","data":{"calendar_id":"cal-123"}}""".getBytes(StandardCharsets.UTF_8)))
                .isEmpty();
        assertThat(RecallCalendarClient.calendarsUpdatedIn("not json".getBytes(StandardCharsets.UTF_8))).isEmpty();

        JsonMapper json = JsonMapper.builder().build();
        assertThat(RecallCalendarClient.statusOf(json.readTree("\"disconnected\"")))
                .isEqualTo(RecallCalendarStatus.DISCONNECTED);
        assertThat(RecallCalendarClient.statusOf(json.readTree("\"connected\"")))
                .isEqualTo(RecallCalendarStatus.CONNECTED);
        assertThat(RecallCalendarClient.statusOf(json.readTree("\"connecting\"")))
                .isEqualTo(RecallCalendarStatus.CONNECTING);
    }

    private static String sign(String id, String timestamp, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(KEY), "HmacSHA256"));
        mac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(mac.doFinal(body));
    }
}
