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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * Gmail and Google Workspace mail through the Gmail API, connected through the OAuth app the workspace chose:
 * Uncava's, or the customer's Internal app. A send is a raw RFC 2822 message ({@link RawEmail}); a follow-up names
 * the thread and carries the last message's {@code Message-ID} in {@code In-Reply-To} and {@code References}, which
 * is what threads it in the executive's own client, Gmail or not. Only headers are ever read ({@code gmail.metadata}),
 * never a body. A send is never retried — see {@link MailboxGateway}.
 */
public class GoogleMailboxGateway extends OAuthDirectMailboxGateway {

    static final String VENDOR = "gmail";
    public static final String API = "https://www.googleapis.com";
    public static final String ACCOUNTS = "https://accounts.google.com";

    /** {@code calendar.*} for meetings, free/busy and booking; together they cover Recall's read too. */
    static final List<String> SCOPES = List.of("openid", "email", "https://www.googleapis.com/auth/gmail.send",
            "https://www.googleapis.com/auth/gmail.metadata", "https://www.googleapis.com/auth/calendar.events",
            "https://www.googleapis.com/auth/calendar.freebusy");

    /** Only what a meeting row keeps: never a description, a location or an attachment. */
    private static final String EVENT_FIELDS = "nextPageToken,items(id,status,summary,start,end,attendees/email,"
            + "organizer/email,recurringEventId,recurrence,hangoutLink,location,"
            + "conferenceData(entryPoints,conferenceSolution/name))";

    private static final int EVENT_PAGE = 250;

    private final String accountsBaseUrl;

    public GoogleMailboxGateway(ProviderCredentialsResolver credentials, ProviderTokenClient tokenEndpoint,
                                MailboxTokens mailboxTokens, VendorClientFactory clientFactory,
                                VendorRateLimiter rateLimiter, VendorCallGuard guard, String apiBaseUrl,
                                String accountsBaseUrl) {
        super(IntegrationProvider.GOOGLE, VENDOR, credentials, tokenEndpoint, mailboxTokens, clientFactory,
                rateLimiter, guard, apiBaseUrl);
        this.accountsBaseUrl = accountsBaseUrl;
    }

    /** {@code prompt=consent} because Google sends a refresh token only on a consent it actually showed. */
    @Override
    protected UriComponentsBuilder consentEndpoint(ProviderCredentials app) {
        return UriComponentsBuilder.fromUriString(accountsBaseUrl)
                .path("/o/oauth2/v2/auth")
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent");
    }

    @Override
    protected List<String> scopes() {
        return SCOPES;
    }

    @Override
    protected String mailboxAddressOf(String accessToken) {
        JsonNode profile = apiCall("profile", accessToken, client -> client.get()
                .uri("/gmail/v1/users/me/profile")
                .accept(MediaType.APPLICATION_JSON));
        return textOrNull(profile == null ? null : profile.get("emailAddress"));
    }

    /** A follow-up reads the last message's own headers first, so it threads wherever the executive reads it. */
    @Override
    public SentEmail send(String grantId, OutgoingEmail email) {
        String accessToken = accessTokenOf(grantId);
        Map<String, Object> message = new LinkedHashMap<>();
        if (email.replyToMessageId() == null) {
            message.put("raw", RawEmail.of(email, null, null).encoded());
        } else {
            JsonNode last = apiCall("reply-headers", accessToken, client -> client.get()
                    .uri(builder -> builder.path("/gmail/v1/users/me/messages/{id}")
                            .queryParam("format", "metadata")
                            .queryParam("metadataHeaders", "Message-ID")
                            .queryParam("metadataHeaders", "References")
                            .build(email.replyToMessageId()))
                    .accept(MediaType.APPLICATION_JSON));
            String threadId = textOrNull(last == null ? null : last.get("threadId"));
            String messageIdHeader = headerOf(last, "Message-ID");
            if (threadId == null || messageIdHeader == null) {
                throw new VendorException(vendorCall("reply-headers"),
                        VendorFailureKind.MALFORMED_RESPONSE, null);
            }
            message.put("threadId", threadId);
            message.put("raw", RawEmail.of(email, messageIdHeader, headerOf(last, "References")).encoded());
        }
        JsonNode sent = apiCall("send", accessToken, client -> client.post()
                .uri("/gmail/v1/users/me/messages/send")
                .contentType(MediaType.APPLICATION_JSON)
                .body(message));
        String messageId = textOrNull(sent == null ? null : sent.get("id"));
        String threadId = textOrNull(sent == null ? null : sent.get("threadId"));
        if (messageId == null || threadId == null) {
            // It was sent: Gmail answered 2xx. Never resent; the run is told the send went with no ids to follow up on.
            throw new VendorException(vendorCall("send"), VendorFailureKind.MALFORMED_RESPONSE, null);
        }
        return new SentEmail(messageId, threadId);
    }

    @Override
    public List<CalendarEvent> calendarEvents(String grantId, Instant from, Instant to) {
        String accessToken = accessTokenOf(grantId);
        return pagedEvents(grantId, (String) null, pageToken -> apiCall("calendar-events", accessToken,
                client -> client.get()
                        .uri(builder -> {
                            builder.path("/calendar/v3/calendars/primary/events")
                                    .queryParam("timeMin", "{from}")
                                    .queryParam("timeMax", "{to}")
                                    .queryParam("singleEvents", true)
                                    .queryParam("maxResults", EVENT_PAGE)
                                    .queryParam("fields", "{fields}");
                            if (pageToken != null) {
                                builder.queryParam("pageToken", "{token}");
                            }
                            return builder.build(Map.of("from", from.toString(), "to", to.toString(),
                                    "fields", EVENT_FIELDS, "token", pageToken == null ? "" : pageToken));
                        })
                        .accept(MediaType.APPLICATION_JSON)),
                "items", GoogleMailboxGateway::eventOf, answer -> textOrNull(answer.get("nextPageToken")));
    }

    @Override
    public List<BusyInterval> busyTimes(String grantId, String address, Instant from, Instant to) {
        String accessToken = accessTokenOf(grantId);
        JsonNode answer = apiCall("free-busy", accessToken, client -> client.post()
                .uri("/calendar/v3/freeBusy")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("timeMin", from.toString(), "timeMax", to.toString(),
                        "items", List.of(Map.of("id", address)))));
        return busyIntervalsOf(answer, address);
    }

    /**
     * Google answers a calendar it could not read with an {@code errors} entry and no busy list. Read as "nothing
     * busy", that offers every slot as free and books an invite into a taken one, so it is a failure.
     */
    List<BusyInterval> busyIntervalsOf(JsonNode answer, String address) {
        JsonNode calendar = answer == null ? null : answer.path("calendars").get(address);
        if (calendar == null || !calendar.path("errors").isEmpty() || !calendar.path("busy").isArray()) {
            throw unreadableCalendar();
        }
        List<BusyInterval> busy = new ArrayList<>();
        for (JsonNode interval : calendar.path("busy")) {
            Instant start = instantOrNull(interval.get("start"));
            Instant end = instantOrNull(interval.get("end"));
            if (start == null || end == null) {
                throw unreadableCalendar();
            }
            busy.add(new BusyInterval(start, end));
        }
        return busy;
    }

    /**
     * {@code sendUpdates=all} is what mails the executive the invite. Meet is created by the calendar itself; a Teams
     * link is not Google's to make, so that invite goes without one. Never retried — see {@link MailboxGateway}.
     */
    @Override
    public CalendarEvent createEvent(String grantId, NewCalendarEvent event) {
        String accessToken = accessTokenOf(grantId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", event.title());
        body.put("start", Map.of("dateTime", event.startsAt().toString()));
        body.put("end", Map.of("dateTime", event.endsAt().toString()));
        body.put("attendees", List.of(Map.of("email", event.inviteeAddress())));
        if (event.joinUrl() != null) {
            body.put("location", event.joinUrl());
            body.put("description", event.joinNote());
        } else if (event.video() == MeetingVideo.GOOGLE_MEET) {
            body.put("conferenceData", Map.of("createRequest", Map.of("requestId", UUID.randomUUID().toString(),
                    "conferenceSolutionKey", Map.of("type", "hangoutsMeet"))));
        }
        JsonNode answer = apiCall("create-event", accessToken, client -> client.post()
                .uri(builder -> builder.path("/calendar/v3/calendars/primary/events")
                        .queryParam("sendUpdates", "all")
                        .queryParam("conferenceDataVersion", 1)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));
        return requireCreated(grantId, answer == null ? null : eventOf(answer));
    }

    /**
     * Null for a cancelled event, one with no id, an all-day one ({@code start.date}, no call), or one of a recurring
     * series: kept at all, a series would be kept once per occurrence and never cleanly removed.
     */
    static CalendarEvent eventOf(JsonNode event) {
        String id = textOrNull(event.get("id"));
        Instant startsAt = instantOrNull(event.path("start").get("dateTime"));
        Instant endsAt = instantOrNull(event.path("end").get("dateTime"));
        boolean recurring = textOrNull(event.get("recurringEventId")) != null || !event.path("recurrence").isEmpty();
        if (id == null || startsAt == null || endsAt == null || recurring
                || "cancelled".equalsIgnoreCase(textOrNull(event.get("status")))) {
            return null;
        }
        List<String> participants = participantsOf(event.path("attendees"), attendee -> attendee.get("email"),
                textOrNull(event.path("organizer").get("email")));
        JsonNode conference = event.path("conferenceData");
        String hangoutLink = textOrNull(event.get("hangoutLink"));
        String joinUrl = videoEntryPointOf(conference);
        String provider = textOrNull(conference.path("conferenceSolution").get("name"));
        if (joinUrl == null && hangoutLink != null) {
            joinUrl = hangoutLink;
            provider = provider == null ? "Google Meet" : provider;
        }
        if (joinUrl == null) {
            joinUrl = ZoomLinks.joinUrlIn(textOrNull(event.get("location")));
            provider = ZoomLinks.PROVIDER;
        }
        return new CalendarEvent(id, textOrNull(event.get("summary")), startsAt, endsAt, participants, joinUrl,
                joinUrl == null ? null : provider);
    }

    private static String videoEntryPointOf(JsonNode conference) {
        for (JsonNode entryPoint : conference.path("entryPoints")) {
            if ("video".equals(textOrNull(entryPoint.get("entryPointType")))) {
                return textOrNull(entryPoint.get("uri"));
            }
        }
        return null;
    }

    /** An RFC 3339 time with any offset; null for an all-day date or anything unreadable. */
    private static Instant instantOrNull(JsonNode node) {
        String text = textOrNull(node);
        if (text == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException unreadable) {
            return null;
        }
    }

    /** Google revokes by the refresh token, which the caller read before the row let it go; none means drop it. */
    @Override
    protected void release(ReleasedGrant released) {
        if (released.refreshToken() != null) {
            revokeRefreshToken(released.refreshToken());
        }
    }

    @Override
    public boolean revokesByRefreshToken(String grantId) {
        return true;
    }

    /**
     * Who wrote into the thread since {@code since}, from each message's {@code From} header alone. The consultant's
     * own sent copies and drafts are left out by label, whatever address they went from.
     */
    @Override
    public List<String> senderAddressesInThread(String grantId, String threadId, Instant since) {
        String accessToken = accessTokenOf(grantId);
        JsonNode thread = apiCall("thread", accessToken, client -> client.get()
                .uri(builder -> builder.path("/gmail/v1/users/me/threads/{id}")
                        .queryParam("format", "metadata")
                        .queryParam("metadataHeaders", "From")
                        .build(threadId))
                .accept(MediaType.APPLICATION_JSON));
        return sendersSince(thread, since);
    }

    static List<String> sendersSince(JsonNode thread, Instant since) {
        List<String> senders = new ArrayList<>();
        if (thread == null) {
            return senders;
        }
        for (JsonNode message : thread.path("messages")) {
            if (hasLabel(message, "SENT") || hasLabel(message, "DRAFT")) {
                continue;
            }
            // A message whose date cannot be read is skipped: read as new, an old one would stop the run.
            long internalDate = message.path("internalDate").asLong(-1);
            String address = RawEmail.addressOf(headerOf(message, "From"));
            if (address != null && internalDate >= 0 && !Instant.ofEpochMilli(internalDate).isBefore(since)) {
                senders.add(address);
            }
        }
        return senders;
    }

    private static boolean hasLabel(JsonNode message, String label) {
        for (JsonNode each : message.path("labelIds")) {
            if (label.equals(each.asString(""))) {
                return true;
            }
        }
        return false;
    }

    static String headerOf(JsonNode message, String name) {
        if (message == null) {
            return null;
        }
        for (JsonNode header : message.path("payload").path("headers")) {
            if (name.equalsIgnoreCase(header.path("name").asString(""))) {
                return textOrNull(header.get("value"));
            }
        }
        return null;
    }
}
