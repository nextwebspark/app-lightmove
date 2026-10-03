package app.lightmove.api.outreach.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.model.BookingPageSpec;
import app.lightmove.api.outreach.model.BusyInterval;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.MailboxEvent;
import app.lightmove.api.outreach.model.MailboxGrants;
import app.lightmove.api.outreach.model.NewCalendarEvent;
import app.lightmove.api.outreach.model.OutgoingEmail;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.ProviderTokenGrant;
import app.lightmove.api.outreach.model.SentEmail;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * Gmail and Google Workspace mail through the Gmail API, connected through the OAuth app the workspace chose:
 * Uncava's, or the customer's Internal app. A send is a raw RFC 2822 message ({@link RawEmail}); a follow-up names
 * the thread and carries the last message's {@code Message-ID} in {@code In-Reply-To} and {@code References}, which
 * is what threads it in the executive's own client, Gmail or not. Only headers are ever read ({@code gmail.metadata}),
 * never a body. A send is never retried — see {@link MailboxGateway}.
 */
public class GoogleMailboxGateway implements DirectMailboxGateway {

    static final String PROVIDER = "google";
    static final String VENDOR = "gmail";
    public static final String API = "https://www.googleapis.com";
    public static final String ACCOUNTS = "https://accounts.google.com";

    /** {@code calendar.*} now, so the calendar (#647) needs no reconnect; together they cover Recall's read too. */
    static final List<String> SCOPES = List.of("openid", "email", "https://www.googleapis.com/auth/gmail.send",
            "https://www.googleapis.com/auth/gmail.metadata", "https://www.googleapis.com/auth/calendar.events",
            "https://www.googleapis.com/auth/calendar.freebusy");

    /** A thread an outreach run writes into is a handful of messages. */
    static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final int REQUESTS_PER_SECOND = 10;

    private final ProviderCredentialsResolver credentials;
    private final ProviderTokenClient tokenEndpoint;
    private final MailboxTokens mailboxTokens;
    private final VendorCallGuard guard;
    private final RestClient api;
    private final String accountsBaseUrl;

    public GoogleMailboxGateway(ProviderCredentialsResolver credentials, ProviderTokenClient tokenEndpoint,
                                MailboxTokens mailboxTokens, VendorClientFactory clientFactory,
                                VendorRateLimiter rateLimiter, VendorCallGuard guard, String apiBaseUrl,
                                String accountsBaseUrl) {
        this.credentials = credentials;
        this.tokenEndpoint = tokenEndpoint;
        this.mailboxTokens = mailboxTokens;
        this.guard = guard;
        this.accountsBaseUrl = accountsBaseUrl;
        this.api = clientFactory.create(new VendorClientSpec(VENDOR, apiBaseUrl, null, null, null, null,
                READ_TIMEOUT, REQUESTS_PER_SECOND), RestClient.builder(), rateLimiter);
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    /** Offered where anyone could connect: Uncava's shared app is configured, or some firm brought its own. */
    @Override
    public boolean isOffered() {
        return credentials.isAnyAppAt(IntegrationProvider.GOOGLE);
    }

    @Override
    public boolean isOfferedTo(UUID workspaceId) {
        return credentials.resolve(workspaceId, IntegrationProvider.GOOGLE).isPresent();
    }

    /** Our app is chosen per workspace, so a sign-in that names none cannot be started here. */
    @Override
    public URI authorizationUri(String provider, String loginHint, String state, URI redirectUri) {
        throw ApiException.of(ErrorCode.MAILBOX_UNAVAILABLE);
    }

    @Override
    public GrantedMailbox redeem(String code, URI redirectUri) {
        throw ApiException.of(ErrorCode.MAILBOX_UNAVAILABLE);
    }

    /** {@code prompt=consent} because Google sends a refresh token only on a consent it actually showed. */
    @Override
    public URI authorizationUri(UUID workspaceId, String provider, String loginHint, String state, URI redirectUri) {
        ProviderCredentials app = requireApp(workspaceId);
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(accountsBaseUrl)
                .path("/o/oauth2/v2/auth")
                .queryParam("client_id", app.clientId())
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", redirectUri.toString())
                .queryParam("scope", String.join(" ", SCOPES))
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("include_granted_scopes", "true")
                .queryParam("state", state);
        if (loginHint != null && !loginHint.isBlank()) {
            uri.queryParam("login_hint", loginHint);
        }
        return uri.encode().build().toUri();
    }

    /** Never retried: a code is single-use. Any refusal here reads to the caller as the connect having failed. */
    @Override
    public GrantedMailbox redeem(UUID workspaceId, String provider, String code, URI redirectUri) {
        ProviderCredentials app = requireApp(workspaceId);
        VendorCall call = VendorCall.of(VENDOR, "redeem");
        ProviderTokenGrant token;
        try {
            token = tokenEndpoint.redeemCode(app, code, redirectUri, List.of());
        } catch (ProviderGrantRefused | ProviderAppUnavailable refused) {
            throw new VendorException(call, VendorFailureKind.BAD_REQUEST, refused);
        }
        if (token.refreshToken() == null) {
            throw new VendorException(call, VendorFailureKind.MALFORMED_RESPONSE, null);
        }
        JsonNode profile = apiCall("profile", token.accessToken(), client -> client.get()
                .uri("/gmail/v1/users/me/profile")
                .accept(MediaType.APPLICATION_JSON));
        String address = textOrNull(profile == null ? null : profile.get("emailAddress"));
        if (address == null) {
            throw new VendorException(VendorCall.of(VENDOR, "profile"), VendorFailureKind.MALFORMED_RESPONSE, null);
        }
        return new GrantedMailbox(MailboxGrants.mintDirect(PROVIDER), address, PROVIDER, token.refreshToken());
    }

    /** A follow-up reads the last message's own headers first, so it threads wherever the executive reads it. */
    @Override
    public SentEmail send(String grantId, OutgoingEmail email) {
        String accessToken = mailboxTokens.accessToken(grantId);
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
                throw new VendorException(VendorCall.of(VENDOR, "reply-headers"),
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
            throw new VendorException(VendorCall.of(VENDOR, "send"), VendorFailureKind.MALFORMED_RESPONSE, null);
        }
        return new SentEmail(messageId, threadId);
    }

    @Override
    public void revoke(String grantId) {
        mailboxTokens.forget(grantId);
    }

    /** Google revokes by the refresh token, which the caller read before the row let it go. */
    @Override
    public void revoke(String grantId, String refreshToken) {
        mailboxTokens.forget(grantId);
        tokenEndpoint.revoke(IntegrationProvider.GOOGLE, refreshToken);
    }

    /** Replies are found by the poll; Gmail push through Pub/Sub is a later follow-up. */
    @Override
    public List<MailboxEvent> readWebhook(String signature, byte[] body) {
        return List.of();
    }

    /**
     * Who wrote into the thread since {@code since}, from each message's {@code From} header alone. The consultant's
     * own sent copies and drafts are left out by label, whatever address they went from.
     */
    @Override
    public List<String> senderAddressesInThread(String grantId, String threadId, Instant since) {
        String accessToken = mailboxTokens.accessToken(grantId);
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
            long internalDate = message.path("internalDate").asLong(Long.MAX_VALUE);
            String address = RawEmail.addressOf(headerOf(message, "From"));
            if (address != null && !Instant.ofEpochMilli(internalDate).isBefore(since)) {
                senders.add(address);
            }
        }
        return senders;
    }

    @Override
    public List<CalendarEvent> calendarEvents(String grantId, Instant from, Instant to) {
        throw calendarNotYet();
    }

    @Override
    public List<BusyInterval> busyTimes(String grantId, String address, Instant from, Instant to) {
        throw calendarNotYet();
    }

    @Override
    public CalendarEvent createEvent(String grantId, NewCalendarEvent event) {
        throw calendarNotYet();
    }

    @Override
    public boolean isBookingPageOffered() {
        return false;
    }

    /** Booking pages are Nylas Scheduler's: a sequence with {@code {{bookingLink}}} cannot start from this mailbox. */
    @Override
    public String createBookingPage(String grantId, BookingPageSpec page) {
        throw ApiException.of(ErrorCode.OUTREACH_BOOKING_LINK_UNAVAILABLE);
    }

    private JsonNode apiCall(String operation, String accessToken,
                             Function<RestClient, RestClient.RequestHeadersSpec<?>> request) {
        return guard.call(VendorCall.of(VENDOR, operation), () -> request.apply(api)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class));
    }

    private ProviderCredentials requireApp(UUID workspaceId) {
        return credentials.resolve(workspaceId, IntegrationProvider.GOOGLE)
                .orElseThrow(() -> ApiException.of(ErrorCode.MAILBOX_UNAVAILABLE));
    }

    private static ApiException calendarNotYet() {
        return ApiException.of(ErrorCode.MAILBOX_CALENDAR_UNSUPPORTED);
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

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() || node.asString("").isBlank() ? null : node.asString("");
    }
}
