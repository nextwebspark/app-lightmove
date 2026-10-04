package app.lightmove.api.outreach.service;

import app.lightmove.api.core.config.NylasSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.outreach.constant.MeetingVideo;
import app.lightmove.api.outreach.model.BookingMade;
import app.lightmove.api.outreach.model.BookingPageSpec;
import app.lightmove.api.outreach.model.BusyInterval;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.CalendarEventChanged;
import app.lightmove.api.outreach.model.CalendarEventRemoved;
import app.lightmove.api.outreach.model.DeliveryFailure;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.InboundMessage;
import app.lightmove.api.outreach.model.MailboxAccessWithdrawn;
import app.lightmove.api.outreach.model.MailboxEvent;
import app.lightmove.api.outreach.model.NewCalendarEvent;
import app.lightmove.api.outreach.model.OutgoingEmail;
import app.lightmove.api.outreach.model.ReleasedGrant;
import app.lightmove.api.outreach.model.SentEmail;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import tools.jackson.databind.json.JsonMapper;

/**
 * Nylas v3: hosted sign-in with the API key, then everything by grant id. Nothing here is retried —
 * see {@link MailboxGateway} for why a send never is, and a redeemed code cannot be redeemed twice.
 */
public class NylasMailboxGateway implements MailboxGateway {

    /** A send waits on the mailbox's own provider, not just on Nylas. */
    static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    static final String VENDOR = "nylas";

    /** Nylas's documented constant for the API-key flow, which asks no PKCE of the caller. */
    private static final String API_KEY_FLOW_VERIFIER = "nylas";

    private static final ObjectMapper WEBHOOK_JSON = JsonMapper.builder().build();

    /** A thread an outreach run writes into is a handful of messages; this is far past any real one. */
    private static final int THREAD_PAGE = 50;

    private static final int EVENT_PAGE = 200;

    /** Two thousand events either side of today is a full diary; a calendar past it is read no further. */
    private static final int MAX_EVENT_PAGES = 10;

    private static final String PRIMARY_CALENDAR = "primary";

    /** How far ahead an executive may book, and how soon: two weeks out, never within the hour. */
    private static final int BOOKING_DAYS_AHEAD = 14;
    private static final int BOOKING_NOTICE_MINUTES = 60;

    private final NylasSettings config;
    private final RestClient client;
    private final VendorCallGuard guard;

    public NylasMailboxGateway(NylasSettings config, VendorClientFactory clientFactory, VendorRateLimiter rateLimiter,
                               VendorCallGuard guard, RestClient.Builder builder) {
        this.config = config;
        this.guard = guard;
        this.client = clientFactory.create(VendorClientSpec.bearer(VENDOR, config.baseUrl(), config.apiKey(),
                READ_TIMEOUT, config.requestsPerSecond()), builder, rateLimiter);
    }

    @Override
    public boolean isOffered() {
        return true;
    }

    @Override
    public List<String> providers() {
        return List.copyOf(config.providers());
    }

    @Override
    public URI authorizationUri(String provider, String loginHint, String state, URI redirectUri) {
        return UriComponentsBuilder.fromUriString(config.baseUrl())
                .path("/v3/connect/auth")
                .queryParam("client_id", config.clientId())
                .queryParam("redirect_uri", redirectUri.toString())
                .queryParam("response_type", "code")
                .queryParam("access_type", "online")
                .queryParam("provider", provider)
                .queryParam("login_hint", loginHint)
                .queryParam("state", state)
                .encode()
                .build()
                .toUri();
    }

    @Override
    public GrantedMailbox redeem(String code, URI redirectUri) {
        TokenAnswer token = guard.call(VendorCall.of(VENDOR, "connect-token"), () -> client.post()
                .uri("/v3/connect/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "client_id", config.clientId(),
                        "client_secret", config.apiKey(),
                        "grant_type", "authorization_code",
                        "code", code,
                        "redirect_uri", redirectUri.toString(),
                        "code_verifier", API_KEY_FLOW_VERIFIER))
                .retrieve()
                .body(TokenAnswer.class));
        GrantAnswer grant = guard.call(VendorCall.of(VENDOR, "grant"), () -> client.get()
                .uri("/v3/grants/{grantId}", token.grantId())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(GrantAnswer.class));
        return new GrantedMailbox(token.grantId(), grant.data().email(), grant.data().provider());
    }

    @Override
    public SentEmail send(String grantId, OutgoingEmail email) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("to", List.of(Map.of("email", email.to())));
        message.put("subject", email.subject());
        message.put("body", email.htmlBody());
        if (email.replyToMessageId() != null) {
            message.put("reply_to_message_id", email.replyToMessageId());
        }
        SendAnswer sent = guard.call(VendorCall.of(VENDOR, "send"), () -> client.post()
                .uri("/v3/grants/{grantId}/messages/send", grantId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(message)
                .retrieve()
                .body(SendAnswer.class));
        return new SentEmail(sent.data().id(), sent.data().threadId());
    }

    @Override
    public void revoke(ReleasedGrant released) {
        guard.call(VendorCall.of(VENDOR, "revoke"), () -> client.delete()
                .uri("/v3/grants/{grantId}", released.grantId())
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public List<MailboxEvent> readWebhook(String signature, byte[] body) {
        if (!signedByNylas(signature, body)) {
            throw ApiException.of(ErrorCode.MAILBOX_WEBHOOK_REJECTED);
        }
        JsonNode notification;
        try {
            notification = WEBHOOK_JSON.readTree(body);
        } catch (JacksonException unreadable) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "A signed webhook delivery was not JSON");
        }
        JsonNode object = notification.path("data").path("object");
        String grantId = textOrNull(object.path("grant_id"));
        if ("booking.created".equals(notification.path("type").asString(""))) {
            return bookingOf(grantId, object);
        }
        if (grantId == null) {
            return List.of();
        }
        return switch (notification.path("type").asString("")) {
            case "message.created", "message.created.truncated" -> List.of(new InboundMessage(grantId,
                    textOrNull(object.path("thread_id")), textOrNull(object.path("from").path(0).path("email"))));
            case "message.bounce_detected" -> List.of(new DeliveryFailure(grantId,
                    firstText(object.path("origin").path("thread_id"), object.path("thread_id")),
                    firstText(object.path("origin").path("id"), object.path("message_id"))));
            case "grant.expired", "grant.deleted" -> List.of(new MailboxAccessWithdrawn(grantId));
            case "event.created", "event.updated" -> calendarChangeOf(grantId, object);
            case "event.deleted" -> eventIdOrEmpty(grantId, object);
            default -> List.of();
        };
    }

    @Override
    public List<CalendarEvent> calendarEvents(String grantId, Instant from, Instant to) {
        List<CalendarEvent> events = new ArrayList<>();
        String pageToken = null;
        for (int page = 0; page < MAX_EVENT_PAGES; page++) {
            String token = pageToken;
            JsonNode answer = guard.call(VendorCall.of(VENDOR, "calendar-events"), () -> client.get()
                    .uri(builder -> {
                        builder.path("/v3/grants/{grantId}/events")
                                .queryParam("calendar_id", PRIMARY_CALENDAR)
                                .queryParam("start", from.getEpochSecond())
                                .queryParam("end", to.getEpochSecond())
                                .queryParam("limit", EVENT_PAGE);
                        if (token != null) {
                            builder.queryParam("page_token", token);
                        }
                        return builder.build(grantId);
                    })
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class));
            if (answer == null) {
                break;
            }
            for (JsonNode event : answer.path("data")) {
                if (!isCancelled(event)) {
                    CalendarEvent read = eventOf(event);
                    if (read != null) {
                        events.add(read);
                    }
                }
            }
            pageToken = textOrNull(answer.path("next_cursor"));
            if (pageToken == null) {
                break;
            }
        }
        return events;
    }

    @Override
    public List<BusyInterval> busyTimes(String grantId, String address, Instant from, Instant to) {
        JsonNode answer = guard.call(VendorCall.of(VENDOR, "free-busy"), () -> client.post()
                .uri("/v3/grants/{grantId}/calendars/free-busy", grantId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("start_time", from.getEpochSecond(), "end_time", to.getEpochSecond(),
                        "emails", List.of(address)))
                .retrieve()
                .body(JsonNode.class));
        return busyIntervalsOf(answer);
    }

    /**
     * Nylas answers a calendar it could not read with an error entry in place of its time slots. Read as
     * "nothing busy", that offers every slot as free and books an invite into a taken one, so it is a failure.
     */
    static List<BusyInterval> busyIntervalsOf(JsonNode answer) {
        if (answer == null || !answer.path("data").isArray()) {
            throw unreadableCalendar();
        }
        List<BusyInterval> busy = new ArrayList<>();
        for (JsonNode calendar : answer.path("data")) {
            if (!"free_busy".equals(textOrNull(calendar.path("object"))) || !calendar.path("time_slots").isArray()) {
                throw unreadableCalendar();
            }
            for (JsonNode slot : calendar.path("time_slots")) {
                String status = textOrNull(slot.path("status"));
                if ((status == null || status.equalsIgnoreCase("busy")) && slot.path("start_time").isNumber()
                        && slot.path("end_time").isNumber()) {
                    busy.add(new BusyInterval(Instant.ofEpochSecond(slot.path("start_time").asLong()),
                            Instant.ofEpochSecond(slot.path("end_time").asLong())));
                }
            }
        }
        return busy;
    }

    private static VendorException unreadableCalendar() {
        return new VendorException(VendorCall.of(VENDOR, "free-busy"), VendorFailureKind.UNAVAILABLE, null);
    }

    @Override
    public CalendarEvent createEvent(String grantId, NewCalendarEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", event.title());
        body.put("when", Map.of("start_time", event.startsAt().getEpochSecond(),
                "end_time", event.endsAt().getEpochSecond()));
        body.put("participants", List.of(Map.of("email", event.inviteeAddress())));
        if (event.joinUrl() != null) {
            body.put("location", event.joinUrl());
            body.put("description", event.joinNote());
        }
        String conferencing = conferencingProviderOf(event.video());
        if (conferencing != null) {
            body.put("conferencing", Map.of("provider", conferencing, "autocreate", Map.of()));
        }
        JsonNode answer = guard.call(VendorCall.of(VENDOR, "create-event"), () -> client.post()
                .uri(builder -> builder.path("/v3/grants/{grantId}/events")
                        .queryParam("calendar_id", PRIMARY_CALENDAR)
                        .queryParam("notify_participants", true)
                        .build(grantId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));
        CalendarEvent created = answer == null ? null : eventOf(answer.path("data"));
        if (created == null) {
            throw new IllegalStateException("The mail service answered an event create with no timed event");
        }
        return created;
    }

    @Override
    public boolean isBookingPageOffered() {
        return config.schedulerEnabled();
    }

    @Override
    public String createBookingPage(String grantId, BookingPageSpec page) {
        Map<String, Object> organizer = new LinkedHashMap<>();
        organizer.put("email", page.organizerAddress());
        organizer.put("name", page.organizerName());
        organizer.put("is_organizer", true);
        organizer.put("availability", Map.of("calendar_ids", List.of(PRIMARY_CALENDAR)));
        organizer.put("booking", Map.of("calendar_id", PRIMARY_CALENDAR));
        Map<String, Object> openHours = Map.of(
                "days", page.days().stream().map(day -> day.getValue() % 7).sorted().toList(),
                "timezone", page.zone().getId(),
                "start", page.dayStart().toString(),
                "end", page.dayEnd().toString(),
                "exdates", List.of());
        Map<String, Object> body = new LinkedHashMap<>();
        // The page is opened by its id from a public link: an executive holds no Nylas session.
        body.put("requires_session_auth", false);
        body.put("participants", List.of(organizer));
        body.put("availability", Map.of("duration_minutes", page.minutes(), "interval_minutes", page.minutes(),
                "availability_rules", Map.of("default_open_hours", List.of(openHours))));
        body.put("event_booking", Map.of("title", page.eventTitle(), "timezone", page.zone().getId()));
        body.put("scheduler", Map.of("available_days_in_future", BOOKING_DAYS_AHEAD,
                "min_booking_notice", BOOKING_NOTICE_MINUTES));
        JsonNode answer = guard.call(VendorCall.of(VENDOR, "create-booking-page"), () -> client.post()
                .uri("/v3/grants/{grantId}/scheduling/configurations", grantId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));
        String configurationId = answer == null ? null : textOrNull(answer.path("data").path("id"));
        if (configurationId == null) {
            throw new VendorException(VendorCall.of(VENDOR, "create-booking-page"),
                    VendorFailureKind.MALFORMED_RESPONSE, null);
        }
        return configurationId;
    }

    /** Nothing in a booking without its configuration and a timed slot can be matched to a page or a call. */
    static List<MailboxEvent> bookingOf(String grantId, JsonNode object) {
        String configurationId = textOrNull(object.path("configuration_id"));
        JsonNode booking = object.path("booking_info");
        if (configurationId == null || !booking.path("start_time").isNumber() || !booking.path("end_time").isNumber()) {
            return List.of();
        }
        List<String> participants = new ArrayList<>();
        for (JsonNode participant : booking.path("participants")) {
            String address = textOrNull(participant.path("email"));
            if (address != null) {
                participants.add(address);
            }
        }
        String guest = textOrNull(booking.path("guest_email"));
        if (guest != null && participants.stream().noneMatch(guest::equalsIgnoreCase)) {
            participants.add(guest);
        }
        return List.of(new BookingMade(grantId, configurationId, textOrNull(booking.path("event_id")),
                textOrNull(booking.path("title")), Instant.ofEpochSecond(booking.path("start_time").asLong()),
                Instant.ofEpochSecond(booking.path("end_time").asLong()), participants));
    }

    private static String conferencingProviderOf(MeetingVideo video) {
        return switch (video) {
            case GOOGLE_MEET -> "Google Meet";
            case MICROSOFT_TEAMS -> "Microsoft Teams";
            case ZOOM, NONE -> null;
        };
    }

    /** A cancelled event, or one that stopped being timed, is as good as gone for a meeting list. */
    private static List<MailboxEvent> calendarChangeOf(String grantId, JsonNode object) {
        CalendarEvent event = isCancelled(object) ? null : eventOf(object);
        return event != null ? List.of(new CalendarEventChanged(grantId, event)) : eventIdOrEmpty(grantId, object);
    }

    private static List<MailboxEvent> eventIdOrEmpty(String grantId, JsonNode object) {
        String eventId = textOrNull(object.path("id"));
        return eventId == null ? List.of() : List.of(new CalendarEventRemoved(grantId, eventId));
    }

    private static boolean isRecurring(JsonNode event) {
        JsonNode recurrence = event.path("recurrence");
        return textOrNull(event.path("master_event_id")) != null
                || (recurrence.isArray() && !recurrence.isEmpty());
    }

    private static boolean isCancelled(JsonNode event) {
        return "cancelled".equalsIgnoreCase(textOrNull(event.path("status")));
    }

    /**
     * Null for an event with no id, no start and end time (an all-day event is no call), or one of a
     * recurring series: a series is keyed by its master in a webhook and by each occurrence in a read,
     * so kept at all it would be kept twice and never cleanly removed.
     */
    static CalendarEvent eventOf(JsonNode event) {
        String id = textOrNull(event.path("id"));
        JsonNode when = event.path("when");
        if (id == null || !when.path("start_time").isNumber() || !when.path("end_time").isNumber()
                || isRecurring(event)) {
            return null;
        }
        List<String> participants = new ArrayList<>();
        for (JsonNode participant : event.path("participants")) {
            String address = textOrNull(participant.path("email"));
            if (address != null) {
                participants.add(address);
            }
        }
        String organizer = textOrNull(event.path("organizer").path("email"));
        if (organizer != null) {
            participants.add(organizer);
        }
        JsonNode conferencing = event.path("conferencing");
        String joinUrl = textOrNull(conferencing.path("details").path("url"));
        String provider = textOrNull(conferencing.path("provider"));
        String zoomUrl = joinUrl == null ? ZoomLinks.joinUrlIn(textOrNull(event.path("location"))) : null;
        if (zoomUrl != null) {
            joinUrl = zoomUrl;
            provider = ZoomLinks.PROVIDER;
        }
        return new CalendarEvent(id, textOrNull(event.path("title")),
                Instant.ofEpochSecond(when.path("start_time").asLong()),
                Instant.ofEpochSecond(when.path("end_time").asLong()), participants, joinUrl, provider);
    }

    @Override
    public List<String> senderAddressesInThread(String grantId, String threadId, Instant since) {
        ThreadMessages messages = guard.call(VendorCall.of(VENDOR, "thread-messages"), () -> client.get()
                .uri(builder -> builder.path("/v3/grants/{grantId}/messages")
                        .queryParam("thread_id", threadId)
                        .queryParam("received_after", since.getEpochSecond())
                        .queryParam("limit", THREAD_PAGE)
                        .queryParam("select", "from")
                        .build(grantId))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ThreadMessages.class));
        if (messages == null || messages.data() == null) {
            return List.of();
        }
        return messages.data().stream()
                .filter(message -> message.from() != null && !message.from().isEmpty())
                .map(message -> message.from().getFirst().email())
                .filter(address -> address != null && !address.isBlank())
                .toList();
    }

    /** Nylas signs the raw body with the webhook's secret: HMAC-SHA256, hex, in {@code X-Nylas-Signature}. */
    private boolean signedByNylas(String signature, byte[] body) {
        String secret = config.webhookSecret();
        if (secret == null || secret.isBlank() || signature == null || body == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = HexFormat.of().formatHex(mac.doFinal(body)).getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("HmacSHA256 is not available", unavailable);
        }
    }

    private static String firstText(JsonNode first, JsonNode second) {
        String value = textOrNull(first);
        return value != null ? value : textOrNull(second);
    }

    private static String textOrNull(JsonNode node) {
        String value = node == null || node.isMissingNode() || node.isNull() ? null : node.asString(null);
        return value == null || value.isBlank() ? null : value;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record TokenAnswer(String grantId) {}

    record GrantAnswer(Grant data) {}

    record Grant(String id, String provider, String email) {}

    record SendAnswer(SentMessage data) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record SentMessage(String id, String threadId) {}

    record ThreadMessages(List<ThreadMessage> data) {}

    record ThreadMessage(List<Participant> from) {}

    record Participant(String email) {}
}
