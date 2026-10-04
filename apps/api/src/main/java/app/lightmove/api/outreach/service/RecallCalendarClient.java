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
import app.lightmove.api.outreach.model.RecallCalendarEvent;
import app.lightmove.api.outreach.model.RecallCalendarSpec;
import app.lightmove.api.outreach.model.RecallWebhookDelivery;
import app.lightmove.api.outreach.model.RecallWebhookNotice;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recall.ai's Calendar V2 over HTTP, {@code Authorization: Token <key>}. Its webhooks are signed the Svix way: an
 * HMAC-SHA256 of {@code id.timestamp.body} under the base64 key after {@code whsec_}, sent as {@code v1,<base64>}.
 */
@Slf4j
public class RecallCalendarClient implements RecallCalendarApi {

    static final String VENDOR = "recall";
    static final Duration READ_TIMEOUT = Duration.ofSeconds(20);
    private static final int REQUESTS_PER_SECOND = 5;

    /** A delivery older or newer than this is refused, so a captured one cannot be replayed later. */
    static final Duration WEBHOOK_TOLERANCE = Duration.ofMinutes(5);

    private static final String SECRET_PREFIX = "whsec_";
    private static final String CALENDAR_UPDATE = "calendar.update";
    private static final String CALENDAR_SYNC_EVENTS = "calendar.sync_events";

    /** A sync names what changed since its last one; a calendar's first sync is the whole of it. */
    static final int MAX_EVENT_PAGES = 50;
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
    public List<RecallWebhookNotice> notices(RecallWebhookDelivery delivery) {
        if (!verifies(config.webhookSecret(), delivery, clock.instant())) {
            throw ApiException.of(ErrorCode.MAILBOX_WEBHOOK_REJECTED);
        }
        return noticesIn(delivery.body());
    }

    /**
     * Only the {@code cursor} of Recall's {@code next} link is taken, and asked again at our own base: the link itself
     * is never followed with our key.
     */
    @Override
    public List<RecallCalendarEvent> eventsUpdatedSince(String calendarId, Instant since) {
        List<RecallCalendarEvent> events = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < MAX_EVENT_PAGES; page++) {
            String pageCursor = cursor;
            JsonNode answer = guard.call(VendorCall.of(VENDOR, "calendar-events"), () -> client.get()
                    .uri(builder -> {
                        builder.path("/api/v2/calendar-events/")
                                .queryParam("calendar_id", "{calendar}")
                                .queryParam("updated_at__gte", "{since}");
                        if (pageCursor != null) {
                            builder.queryParam("cursor", "{cursor}");
                        }
                        return builder.build(Map.of("calendar", calendarId, "since", since.toString(),
                                "cursor", pageCursor == null ? "" : pageCursor));
                    })
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class));
            if (answer == null) {
                return events;
            }
            for (JsonNode event : answer.path("results")) {
                events.add(new RecallCalendarEvent(textOrNull(event.get("platform")),
                        textOrNull(event.get("platform_id")), textOrNull(event.get("ical_uid")), event.get("raw"),
                        event.path("is_deleted").asBoolean(false)));
            }
            cursor = cursorOf(textOrNull(answer.get("next")));
            if (cursor == null) {
                return events;
            }
        }
        log.warn("Recall calendar {} listed more than {} pages of changed events; the rest are not read",
                calendarId, MAX_EVENT_PAGES);
        return events;
    }

    static String cursorOf(String nextLink) {
        if (nextLink == null) {
            return null;
        }
        String cursor = UriComponentsBuilder.fromUriString(nextLink).build().getQueryParams().getFirst("cursor");
        return cursor == null || cursor.isBlank() ? null : UriUtils.decode(cursor, StandardCharsets.UTF_8);
    }

    static List<RecallWebhookNotice> noticesIn(byte[] body) {
        JsonNode payload;
        try {
            payload = JSON.readTree(body);
        } catch (JacksonException unreadable) {
            return List.of();
        }
        if (payload == null) {
            return List.of();
        }
        String calendarId = textOrNull(payload.path("data").get("calendar_id"));
        if (calendarId == null) {
            return List.of();
        }
        return switch (payload.path("event").asText()) {
            case CALENDAR_UPDATE -> List.of(new RecallWebhookNotice.CalendarStateChanged(calendarId));
            case CALENDAR_SYNC_EVENTS -> {
                Instant since = instantOrNull(textOrNull(payload.path("data").get("last_updated_ts")));
                yield since == null ? List.of() : List.of(new RecallWebhookNotice.CalendarEventsChanged(calendarId,
                        since));
            }
            default -> List.of();
        };
    }

    private static Instant instantOrNull(String text) {
        if (text == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException unreadable) {
            return null;
        }
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() || node.asText().isBlank() ? null : node.asText();
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
