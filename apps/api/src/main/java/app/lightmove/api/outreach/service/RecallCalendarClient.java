package app.lightmove.api.outreach.service;

import app.lightmove.api.core.config.RecallSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.outreach.constant.RecallCalendarStatus;
import app.lightmove.api.outreach.model.RecallCalendarSpec;
import app.lightmove.api.outreach.model.RecallWebhookDelivery;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recall.ai's Calendar V2 over HTTP, {@code Authorization: Token <key>}. Its webhooks are signed the Svix way: an
 * HMAC-SHA256 of {@code id.timestamp.body} under the base64 key after {@code whsec_}, sent as {@code v1,<base64>}.
 */
public class RecallCalendarClient implements RecallCalendarApi {

    static final String VENDOR = "recall";
    static final Duration READ_TIMEOUT = Duration.ofSeconds(20);
    private static final int REQUESTS_PER_SECOND = 5;

    /** A delivery older or newer than this is refused, so a captured one cannot be replayed later. */
    static final Duration WEBHOOK_TOLERANCE = Duration.ofMinutes(5);

    private static final String SECRET_PREFIX = "whsec_";
    private static final String CALENDAR_UPDATE = "calendar.update";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RecallSettings config;
    private final RestClient client;
    private final VendorCallGuard guard;
    private final Clock clock;

    public RecallCalendarClient(RecallSettings config, VendorClientFactory clientFactory, VendorRateLimiter rateLimiter,
                                VendorCallGuard guard, RestClient.Builder builder, Clock clock) {
        this.config = config;
        this.guard = guard;
        this.clock = clock;
        this.client = clientFactory.create(VendorClientSpec.header(VENDOR, config.baseUrl(), "Authorization",
                "Token " + config.apiKey(), READ_TIMEOUT, REQUESTS_PER_SECOND), builder, rateLimiter);
    }

    @Override
    public boolean isOffered() {
        return true;
    }

    @Override
    public String create(RecallCalendarSpec spec) {
        JsonNode created = guard.call(VendorCall.of(VENDOR, "calendar-create"), () -> client.post()
                .uri("/api/v2/calendars/")
                .contentType(MediaType.APPLICATION_JSON)
                .body(bodyOf(spec, true))
                .retrieve()
                .body(JsonNode.class));
        String id = created == null || created.get("id") == null ? null : created.get("id").asText();
        if (id == null || id.isBlank()) {
            throw new VendorException(VendorCall.of(VENDOR, "calendar-create"), VendorFailureKind.MALFORMED_RESPONSE,
                    null);
        }
        return id;
    }

    @Override
    public void update(String calendarId, RecallCalendarSpec spec) {
        guard.call(VendorCall.of(VENDOR, "calendar-update"), () -> client.patch()
                .uri("/api/v2/calendars/{id}/", calendarId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(bodyOf(spec, false))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void delete(String calendarId) {
        guard.call(VendorCall.of(VENDOR, "calendar-delete"), () -> client.delete()
                .uri("/api/v2/calendars/{id}/", calendarId)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public RecallCalendarStatus status(String calendarId) {
        JsonNode calendar = guard.call(VendorCall.of(VENDOR, "calendar-get"), () -> client.get()
                .uri("/api/v2/calendars/{id}/", calendarId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class));
        return statusOf(calendar == null ? null : calendar.get("status"));
    }

    @Override
    public List<String> updatedCalendars(RecallWebhookDelivery delivery) {
        if (!verifies(config.webhookSecret(), delivery, clock.instant())) {
            throw ApiException.of(ErrorCode.MAILBOX_WEBHOOK_REJECTED);
        }
        return calendarsUpdatedIn(delivery.body());
    }

    static List<String> calendarsUpdatedIn(byte[] body) {
        JsonNode payload;
        try {
            payload = JSON.readTree(body);
        } catch (JacksonException unreadable) {
            return List.of();
        }
        if (payload == null || !CALENDAR_UPDATE.equals(payload.path("event").asText())) {
            return List.of();
        }
        String calendarId = payload.path("data").path("calendar_id").asText();
        return calendarId.isBlank() ? List.of() : List.of(calendarId);
    }

    static RecallCalendarStatus statusOf(JsonNode status) {
        String value = status == null ? "" : status.asText().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "connected" -> RecallCalendarStatus.CONNECTED;
            case "disconnected" -> RecallCalendarStatus.DISCONNECTED;
            default -> RecallCalendarStatus.CONNECTING;
        };
    }

    /** A blank secret refuses every delivery: an unsigned public endpoint would let anyone disconnect a mailbox. */
    static boolean verifies(String secret, RecallWebhookDelivery delivery, Instant now) {
        if (secret == null || secret.isBlank() || delivery.id() == null || delivery.timestamp() == null
                || delivery.signature() == null || delivery.body() == null) {
            return false;
        }
        long sentAt;
        try {
            sentAt = Long.parseLong(delivery.timestamp().strip());
        } catch (NumberFormatException notATimestamp) {
            return false;
        }
        if (Duration.between(Instant.ofEpochSecond(sentAt), now).abs().compareTo(WEBHOOK_TOLERANCE) > 0) {
            return false;
        }
        byte[] expected;
        try {
            String key = secret.startsWith(SECRET_PREFIX) ? secret.substring(SECRET_PREFIX.length()) : secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(key), "HmacSHA256"));
            mac.update((delivery.id() + "." + delivery.timestamp().strip() + ".").getBytes(StandardCharsets.UTF_8));
            expected = mac.doFinal(delivery.body());
        } catch (GeneralSecurityException | IllegalArgumentException unusable) {
            return false;
        }
        for (String candidate : delivery.signature().split(" ")) {
            int comma = candidate.indexOf(',');
            if (comma < 0 || !"v1".equals(candidate.substring(0, comma))) {
                continue;
            }
            byte[] presented;
            try {
                presented = Base64.getDecoder().decode(candidate.substring(comma + 1));
            } catch (IllegalArgumentException notBase64) {
                continue;
            }
            if (MessageDigest.isEqual(expected, presented)) {
                return true;
            }
        }
        return false;
    }

    /** A create names the host; an update hands over the app and the new token only. */
    private static Map<String, Object> bodyOf(RecallCalendarSpec spec, boolean creating) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (creating) {
            body.put("platform", spec.platform());
        }
        body.put("oauth_client_id", spec.clientId());
        body.put("oauth_client_secret", spec.clientSecret());
        body.put("oauth_refresh_token", spec.refreshToken());
        if (spec.email() != null) {
            body.put("oauth_email", spec.email());
        }
        return body;
    }
}
