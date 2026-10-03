package app.lightmove.api.outreach.service;

import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.constant.MeetingVideo;
import app.lightmove.api.outreach.model.BusyInterval;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.NewCalendarEvent;
import app.lightmove.api.outreach.model.OutgoingEmail;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.ReleasedGrant;
import app.lightmove.api.outreach.model.SentEmail;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;

/**
 * Outlook and Microsoft 365 mail through Microsoft Graph, connected through the OAuth app the workspace chose:
 * Uncava's multi-tenant app at {@code /organizations}, or the customer's single-tenant one at its own directory.
 *
 * <p>Every email is a draft and then a send, never {@code sendMail}: only a draft answers with the message's id and
 * conversation id, which a follow-up replies to and the reply poll reads. Ids are requested immutable, so they
 * survive the message moving to Sent Items. A send is never retried — see {@link MailboxGateway}.
 */
public class MicrosoftMailboxGateway extends OAuthDirectMailboxGateway {

    static final String VENDOR = "microsoft-graph";
    public static final String GRAPH = "https://graph.microsoft.com";
    public static final String LOGIN = "https://login.microsoftonline.com";

    /**
     * {@code Mail.ReadWrite} because a draft is what answers with the ids threading needs, {@code Calendars.ReadWrite}
     * for meetings, free/busy and booking. These also cover Recall's calendar read.
     */
    static final List<String> SCOPES = List.of("offline_access", "User.Read", "Mail.ReadWrite", "Mail.Send",
            "Calendars.ReadWrite");

    private static final String IMMUTABLE_IDS = "IdType=\"ImmutableId\"";

    /** Every time Graph answers is then UTC, whatever zone the consultant's calendar is kept in. */
    private static final String UTC_TIMES = "outlook.timezone=\"UTC\"";

    /** Only what a meeting row keeps: never a body, a location or an attachment. */
    private static final String EVENT_FIELDS = "id,iCalUId,subject,start,end,isAllDay,isCancelled,type,seriesMasterId,"
            + "attendees,organizer,onlineMeeting,onlineMeetingProvider,location";

    private static final int EVENT_PAGE = 250;

    private static final String TEAMS = "teamsForBusiness";

    /**
     * The only statuses offered as free time. Everything else is taken — {@code unknown} and a missing status
     * included, since a calendar Graph could not resolve is never free.
     */
    private static final Set<String> FREE_STATUSES = Set.of("free", "workingElsewhere");

    /** A thread an outreach run writes into is a handful of messages; this is far past any real one. */
    private static final int THREAD_PAGE = 50;

    private final String loginBaseUrl;
    private final Map<String, String> sentItemsFolders = new ConcurrentHashMap<>();

    public MicrosoftMailboxGateway(ProviderCredentialsResolver credentials, ProviderTokenClient tokenEndpoint,
                                   MailboxTokens mailboxTokens, VendorClientFactory clientFactory,
                                   VendorRateLimiter rateLimiter, VendorCallGuard guard, String graphBaseUrl,
                                   String loginBaseUrl) {
        super(IntegrationProvider.MICROSOFT, VENDOR, credentials, tokenEndpoint, mailboxTokens,
                clientFactory, rateLimiter, guard, graphBaseUrl);
        this.loginBaseUrl = loginBaseUrl;
    }

    @Override
    protected UriComponentsBuilder consentEndpoint(ProviderCredentials app) {
        return UriComponentsBuilder.fromUriString(loginBaseUrl)
                .pathSegment(OAuthProviderTokenClient.tenantOf(app), "oauth2", "v2.0", "authorize")
                .queryParam("response_mode", "query")
                .queryParam("prompt", "select_account");
    }

    @Override
    protected List<String> scopes() {
        return SCOPES;
    }

    @Override
    protected boolean repeatsScopesAtRedemption() {
        return true;
    }

    @Override
    protected String mailboxAddressOf(String accessToken) {
        JsonNode me = apiCall("me", accessToken, client -> client.get()
                .uri("/v1.0/me?$select=mail,userPrincipalName")
                .accept(MediaType.APPLICATION_JSON));
        String address = textOrNull(me == null ? null : me.get("mail"));
        return address != null ? address : textOrNull(me == null ? null : me.get("userPrincipalName"));
    }

    /**
     * Ids are asked immutable, so the ones a follow-up, the poll and a meeting key on survive a move between folders;
     * times are asked in UTC.
     */
    @Override
    protected RestClient.RequestHeadersSpec<?> withProviderHeaders(RestClient.RequestHeadersSpec<?> request) {
        return request.header("Prefer", IMMUTABLE_IDS, UTC_TIMES);
    }

    /**
     * A draft, then its send. A follow-up is a reply drafted on the last message, which keeps the conversation; that
     * message is our own, so the reply would go back to the consultant — its recipients are set to the executive
     * alone before it is sent, whatever Graph drafted.
     */
    @Override
    public SentEmail send(String grantId, OutgoingEmail email) {
        String accessToken = accessTokenOf(grantId);
        Map<String, Object> message = messageOf(email);
        JsonNode draft = email.replyToMessageId() == null
                ? apiCall("draft", accessToken, client -> client.post()
                        .uri("/v1.0/me/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(message))
                : apiCall("draft-reply", accessToken, client -> client.post()
                        .uri("/v1.0/me/messages/{id}/createReply", email.replyToMessageId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("message", message)));
        String messageId = textOrNull(draft == null ? null : draft.get("id"));
        String conversationId = textOrNull(draft == null ? null : draft.get("conversationId"));
        if (messageId == null || conversationId == null) {
            throw new VendorException(vendorCall("draft"), VendorFailureKind.MALFORMED_RESPONSE, null);
        }
        try {
            if (email.replyToMessageId() != null) {
                apiCall("address-reply", accessToken, client -> client.patch()
                        .uri("/v1.0/me/messages/{id}", messageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(recipientsOnly(email)));
            }
            apiExchange("send", accessToken, client -> client.post()
                    .uri("/v1.0/me/messages/{id}/send", messageId));
        } catch (VendorException failed) {
            discardUnsentDraft(accessToken, messageId, failed);
            throw failed;
        }
        return new SentEmail(messageId, conversationId);
    }

    /** A recurring series is never kept: {@code calendarView} answers its occurrences, which {@link #eventOf} drops. */
    @Override
    public List<CalendarEvent> calendarEvents(String grantId, Instant from, Instant to) {
        String accessToken = accessTokenOf(grantId);
        return pagedEvents(grantId, Map.<String, String>of(), paging -> apiCall("calendar-events", accessToken,
                client -> client.get()
                        .uri(builder -> {
                            builder.path("/v1.0/me/calendarView")
                                    .queryParam("startDateTime", "{from}")
                                    .queryParam("endDateTime", "{to}")
                                    .queryParam("$select", "{select}")
                                    .queryParam("$top", EVENT_PAGE);
                            Map<String, Object> values = new LinkedHashMap<>(Map.of("from", from.toString(),
                                    "to", to.toString(), "select", EVENT_FIELDS));
                            paging.forEach((name, value) -> {
                                builder.queryParam(name, "{" + name.replace("$", "") + "}");
                                values.put(name.replace("$", ""), value);
                            });
                            return builder.build(values);
                        })
                        .accept(MediaType.APPLICATION_JSON)),
                "value", MicrosoftMailboxGateway::eventOf, answer -> {
                    Map<String, String> next = pagingOf(textOrNull(answer.get("@odata.nextLink")));
                    return next.isEmpty() ? null : next;
                });
    }

    /**
     * Only the paging parameters of Graph's {@code @odata.nextLink} are taken, and asked again at our own base:
     * the link itself is never followed with the consultant's token.
     */
    static Map<String, String> pagingOf(String nextLink) {
        if (nextLink == null) {
            return Map.of();
        }
        Map<String, String> paging = new LinkedHashMap<>();
        UriComponentsBuilder.fromUriString(nextLink).build().getQueryParams().forEach((name, values) -> {
            String decoded = UriUtils.decode(name, StandardCharsets.UTF_8);
            if ((decoded.equals("$skip") || decoded.equals("$skiptoken")) && !values.isEmpty()
                    && values.getFirst() != null) {
                paging.put(decoded, UriUtils.decode(values.getFirst(), StandardCharsets.UTF_8));
            }
        });
        return paging;
    }

    @Override
    public List<BusyInterval> busyTimes(String grantId, String address, Instant from, Instant to) {
        String accessToken = accessTokenOf(grantId);
        JsonNode answer = apiCall("free-busy", accessToken, client -> client.post()
                .uri("/v1.0/me/calendar/getSchedule")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("schedules", List.of(address),
                        "startTime", graphTimeOf(from),
                        "endTime", graphTimeOf(to))));
        return busyIntervalsOf(answer);
    }

    /**
     * Graph answers a calendar it could not read with an {@code error} in place of its items. Read as "nothing busy",
     * that offers every slot as free and books an invite into a taken one, so it is a failure.
     */
    List<BusyInterval> busyIntervalsOf(JsonNode answer) {
        if (answer == null || !answer.path("value").isArray() || answer.path("value").isEmpty()) {
            throw unreadableCalendar();
        }
        List<BusyInterval> busy = new ArrayList<>();
        for (JsonNode schedule : answer.path("value")) {
            if (carriesError(schedule) || !schedule.path("scheduleItems").isArray()) {
                throw unreadableCalendar();
            }
            for (JsonNode item : schedule.path("scheduleItems")) {
                String status = textOrNull(item.get("status"));
                if (status != null && FREE_STATUSES.contains(status)) {
                    continue;
                }
                Instant start = graphInstantOf(item.path("start"));
                Instant end = graphInstantOf(item.path("end"));
                if (start == null || end == null) {
                    throw unreadableCalendar();
                }
                busy.add(new BusyInterval(start, end));
            }
        }
        return busy;
    }

    private static boolean carriesError(JsonNode schedule) {
        JsonNode error = schedule.path("error");
        return !error.isMissingNode() && !error.isNull();
    }

    /**
     * Graph mails the invite itself. A Teams link is made only where the calendar offers Teams — an organisation
     * without a Teams licence or with it turned off does not, and its invite goes without one, as does one asking for
     * Meet. Never retried — see {@link MailboxGateway}.
     */
    @Override
    public CalendarEvent createEvent(String grantId, NewCalendarEvent event) {
        String accessToken = accessTokenOf(grantId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subject", event.title());
        body.put("start", graphTimeOf(event.startsAt()));
        body.put("end", graphTimeOf(event.endsAt()));
        body.put("attendees", List.of(Map.of("emailAddress", Map.of("address", event.inviteeAddress()),
                "type", "required")));
        if (event.joinUrl() != null) {
            body.put("location", Map.of("displayName", event.joinUrl()));
            body.put("body", Map.of("contentType", "text", "content", event.joinNote()));
        } else if (event.video() == MeetingVideo.MICROSOFT_TEAMS && offersTeams(accessToken)) {
            body.put("isOnlineMeeting", true);
            body.put("onlineMeetingProvider", TEAMS);
        }
        JsonNode answer = apiCall("create-event", accessToken, client -> client.post()
                .uri("/v1.0/me/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));
        return requireCreated(grantId, answer == null ? null : eventOf(answer));
    }

    /** Read before the create rather than after a refusal: a create that failed is never tried again. */
    private boolean offersTeams(String accessToken) {
        JsonNode calendar = apiCall("calendar", accessToken, client -> client.get()
                .uri("/v1.0/me/calendar?$select=allowedOnlineMeetingProviders")
                .accept(MediaType.APPLICATION_JSON));
        if (calendar == null) {
            return false;
        }
        for (JsonNode provider : calendar.path("allowedOnlineMeetingProviders")) {
            if (TEAMS.equals(textOrNull(provider))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Null for a cancelled event, one with no id, an all-day one (no call), or one of a recurring series: kept at all,
     * a series would be kept once per occurrence and never cleanly removed. Keyed on {@code iCalUId}, which Recall's
     * copy of the event carries too, where Graph's own id differs between immutable and ordinary requests.
     */
    static CalendarEvent eventOf(JsonNode event) {
        String id = textOrNull(event.get("iCalUId"));
        if (id == null) {
            id = textOrNull(event.get("id"));
        }
        Instant startsAt = graphInstantOf(event.path("start"));
        Instant endsAt = graphInstantOf(event.path("end"));
        String type = textOrNull(event.get("type"));
        boolean recurring = textOrNull(event.get("seriesMasterId")) != null
                || (type != null && !type.equals("singleInstance"));
        if (id == null || startsAt == null || endsAt == null || recurring
                || event.path("isAllDay").asBoolean(false) || event.path("isCancelled").asBoolean(false)) {
            return null;
        }
        List<String> participants = participantsOf(event.path("attendees"),
                attendee -> attendee.path("emailAddress").get("address"),
                textOrNull(event.path("organizer").path("emailAddress").get("address")));
        String joinUrl = textOrNull(event.path("onlineMeeting").get("joinUrl"));
        String provider = joinUrl == null ? null : videoProviderOf(textOrNull(event.get("onlineMeetingProvider")));
        if (joinUrl == null) {
            joinUrl = ZoomLinks.joinUrlIn(textOrNull(event.path("location").get("displayName")));
            provider = joinUrl == null ? null : ZoomLinks.PROVIDER;
        }
        return new CalendarEvent(id, textOrNull(event.get("subject")), startsAt, endsAt, participants, joinUrl,
                provider);
    }

    private static String videoProviderOf(String onlineMeetingProvider) {
        if (onlineMeetingProvider == null) {
            return null;
        }
        return switch (onlineMeetingProvider) {
            case TEAMS, "teamsForConsumer" -> "Microsoft Teams";
            case "skypeForBusiness", "skypeForConsumer" -> "Skype";
            default -> null;
        };
    }

    private static Map<String, String> graphTimeOf(Instant instant) {
        String local = LocalDateTime.ofInstant(instant, ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return Map.of("dateTime", local, "timeZone", "UTC");
    }

    /**
     * Graph's {@code dateTimeTimeZone}: a local time and the zone it is in, UTC as asked. A Windows zone name it
     * answers despite that is unreadable here, and the time with it.
     */
    static Instant graphInstantOf(JsonNode dateTimeTimeZone) {
        String local = textOrNull(dateTimeTimeZone.get("dateTime"));
        String zone = textOrNull(dateTimeTimeZone.get("timeZone"));
        if (local == null) {
            return null;
        }
        try {
            ZoneId zoneId = zone == null || zone.equalsIgnoreCase("UTC") ? ZoneOffset.UTC : ZoneId.of(zone);
            return LocalDateTime.parse(local).atZone(zoneId).toInstant();
        } catch (DateTimeException unreadable) {
            return null;
        }
    }

    /** Graph has no per-app revoke: the stored token goes with the row. */
    @Override
    protected void release(ReleasedGrant released) {
        sentItemsFolders.remove(released.grantId());
    }

    /**
     * Who wrote into the conversation since {@code since}: the sender's address only, never subject or body. Drafts
     * and the consultant's own Sent Items are left out by folder, so their own mail never reads as a reply even where
     * their sending address is not the one the mailbox was connected as.
     */
    @Override
    public List<String> senderAddressesInThread(String grantId, String threadId, Instant since) {
        String accessToken = accessTokenOf(grantId);
        String sentItems = sentItemsFolders.computeIfAbsent(grantId, grant -> sentItemsFolderId(accessToken));
        String filter = "conversationId eq '" + threadId.replace("'", "''") + "'";
        JsonNode page = apiCall("thread", accessToken, client -> client.get()
                .uri(builder -> builder.path("/v1.0/me/messages")
                        .queryParam("$filter", "{filter}")
                        .queryParam("$select", "from,receivedDateTime,isDraft,parentFolderId")
                        .queryParam("$top", THREAD_PAGE)
                        .build(filter))
                .accept(MediaType.APPLICATION_JSON));
        return sendersSince(page, since, sentItems);
    }

    static List<String> sendersSince(JsonNode page, Instant since, String sentItemsFolderId) {
        List<String> senders = new ArrayList<>();
        if (page == null) {
            return senders;
        }
        for (JsonNode message : page.path("value")) {
            String folder = textOrNull(message.get("parentFolderId"));
            if (message.path("isDraft").asBoolean(false) || (folder != null && folder.equals(sentItemsFolderId))) {
                continue;
            }
            Instant received = instantOrNull(message.get("receivedDateTime"));
            String address = textOrNull(message.path("from").path("emailAddress").get("address"));
            if (address != null && (received == null || !received.isBefore(since))) {
                senders.add(address);
            }
        }
        return senders;
    }

    /** Every recipient list set outright, so nothing Graph drafted from the replied-to message survives. */
    static Map<String, Object> recipientsOnly(OutgoingEmail email) {
        return Map.of(
                "toRecipients", List.of(Map.of("emailAddress", Map.of("address", email.to()))),
                "ccRecipients", List.of(),
                "bccRecipients", List.of());
    }

    static Map<String, Object> messageOf(OutgoingEmail email) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("subject", email.subject());
        message.put("body", Map.of("contentType", "HTML", "content", email.htmlBody()));
        message.put("toRecipients", List.of(Map.of("emailAddress", Map.of("address", email.to()))));
        return message;
    }

    /**
     * A definite refusal leaves a finished approach in the consultant's Drafts, one click from a second send; it is
     * deleted. A timeout or an outage may have sent it, so it is left alone.
     */
    private void discardUnsentDraft(String accessToken, String messageId, VendorException failed) {
        if (failed.getKind() == VendorFailureKind.TIMEOUT || failed.getKind() == VendorFailureKind.UNAVAILABLE) {
            return;
        }
        try {
            apiExchange("discard-draft", accessToken, client -> client.delete()
                    .uri("/v1.0/me/messages/{id}", messageId));
        } catch (RuntimeException ignored) {
            // The send's own failure is what the caller needs; a draft left behind is the lesser problem.
        }
    }

    /** Sent Items by its well-known name; null when it cannot be read, and the address comparison stands alone. */
    private String sentItemsFolderId(String accessToken) {
        try {
            JsonNode folder = apiCall("sent-items", accessToken, client -> client.get()
                    .uri("/v1.0/me/mailFolders/sentitems?$select=id")
                    .accept(MediaType.APPLICATION_JSON));
            return textOrNull(folder == null ? null : folder.get("id"));
        } catch (VendorException unreadable) {
            return null;
        }
    }

    private static Instant instantOrNull(JsonNode node) {
        String text = textOrNull(node);
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException unreadable) {
            return null;
        }
    }
}
