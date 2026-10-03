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
import app.lightmove.api.outreach.model.RefreshedAccessToken;
import app.lightmove.api.outreach.model.SentEmail;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
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
 * Outlook and Microsoft 365 mail through Microsoft Graph, connected through the OAuth app the workspace chose:
 * Uncava's multi-tenant app at {@code /organizations}, or the customer's single-tenant one at its own directory.
 *
 * <p>Every email is a draft and then a send, never {@code sendMail}: only a draft answers with the message's id and
 * conversation id, which a follow-up replies to and the reply poll reads. Ids are requested immutable, so they
 * survive the message moving to Sent Items. A send is never retried — see {@link MailboxGateway}.
 */
public class MicrosoftMailboxGateway implements DirectMailboxGateway {

    static final String PROVIDER = "microsoft";
    static final String VENDOR = "microsoft-graph";
    public static final String GRAPH = "https://graph.microsoft.com";
    public static final String LOGIN = "https://login.microsoftonline.com";

    /**
     * {@code Mail.ReadWrite} because a draft is what answers with the ids threading needs, {@code Calendars.ReadWrite}
     * now so the calendar (#647) needs no reconnect. These also cover Recall's calendar read.
     */
    static final List<String> SCOPES = List.of("offline_access", "User.Read", "Mail.ReadWrite", "Mail.Send",
            "Calendars.ReadWrite");

    private static final String IMMUTABLE_IDS = "IdType=\"ImmutableId\"";

    /** A thread an outreach run writes into is a handful of messages; this is far past any real one. */
    private static final int THREAD_PAGE = 50;

    static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final int REQUESTS_PER_SECOND = 10;

    private final ProviderCredentialsResolver credentials;
    private final ProviderTokenClient tokenEndpoint;
    private final MailboxTokens mailboxTokens;
    private final VendorCallGuard guard;
    private final RestClient graph;
    private final String loginBaseUrl;

    public MicrosoftMailboxGateway(ProviderCredentialsResolver credentials, ProviderTokenClient tokenEndpoint,
                                   MailboxTokens mailboxTokens, VendorClientFactory clientFactory,
                                   VendorRateLimiter rateLimiter, VendorCallGuard guard, String graphBaseUrl,
                                   String loginBaseUrl) {
        this.credentials = credentials;
        this.tokenEndpoint = tokenEndpoint;
        this.mailboxTokens = mailboxTokens;
        this.guard = guard;
        this.loginBaseUrl = loginBaseUrl;
        this.graph = clientFactory.create(new VendorClientSpec(VENDOR, graphBaseUrl, null, null, null, null,
                READ_TIMEOUT, REQUESTS_PER_SECOND), RestClient.builder(), rateLimiter);
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public boolean isOffered() {
        return true;
    }

    @Override
    public boolean isOfferedTo(UUID workspaceId) {
        return credentials.resolve(workspaceId, IntegrationProvider.MICROSOFT).isPresent();
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

    @Override
    public URI authorizationUri(UUID workspaceId, String provider, String loginHint, String state, URI redirectUri) {
        ProviderCredentials app = requireApp(workspaceId);
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(loginBaseUrl)
                .pathSegment(OAuthProviderTokenClient.tenantOf(app), "oauth2", "v2.0", "authorize")
                .queryParam("client_id", app.clientId())
                .queryParam("response_type", "code")
                .queryParam("response_mode", "query")
                .queryParam("redirect_uri", redirectUri.toString())
                .queryParam("scope", String.join(" ", SCOPES))
                .queryParam("state", state)
                .queryParam("prompt", "select_account");
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
        RefreshedAccessToken token;
        try {
            token = tokenEndpoint.redeemCode(app, code, redirectUri, SCOPES);
        } catch (RefreshTokenRefused | ProviderAppUnavailable refused) {
            throw new VendorException(call, VendorFailureKind.BAD_REQUEST, refused);
        }
        if (token.refreshToken() == null) {
            // offline_access was not granted; without a refresh token the mailbox dies within the hour.
            throw new VendorException(call, VendorFailureKind.MALFORMED_RESPONSE, null);
        }
        JsonNode me = guard.call(VendorCall.of(VENDOR, "me"), () -> graph.get()
                .uri("/v1.0/me?$select=mail,userPrincipalName")
                .header("Authorization", "Bearer " + token.accessToken())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class));
        String address = textOrNull(me == null ? null : me.get("mail"));
        if (address == null) {
            address = textOrNull(me == null ? null : me.get("userPrincipalName"));
        }
        if (address == null) {
            throw new VendorException(VendorCall.of(VENDOR, "me"), VendorFailureKind.MALFORMED_RESPONSE, null);
        }
        return new GrantedMailbox(MailboxGrants.mintDirect(PROVIDER), address, PROVIDER, token.refreshToken());
    }

    /** A draft, then its send; a follow-up is a reply drafted on the last message, which keeps the conversation. */
    @Override
    public SentEmail send(String grantId, OutgoingEmail email) {
        String accessToken = mailboxTokens.accessToken(grantId);
        Map<String, Object> message = messageOf(email);
        JsonNode draft = email.replyToMessageId() == null
                ? graphCall("draft", accessToken, client -> client.post()
                        .uri("/v1.0/me/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(message))
                : graphCall("draft-reply", accessToken, client -> client.post()
                        .uri("/v1.0/me/messages/{id}/createReply", email.replyToMessageId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("message", message)));
        String messageId = textOrNull(draft == null ? null : draft.get("id"));
        String conversationId = textOrNull(draft == null ? null : draft.get("conversationId"));
        if (messageId == null || conversationId == null) {
            throw new VendorException(VendorCall.of(VENDOR, "draft"), VendorFailureKind.MALFORMED_RESPONSE, null);
        }
        guard.call(VendorCall.of(VENDOR, "send"), () -> graph.post()
                .uri("/v1.0/me/messages/{id}/send", messageId)
                .header("Authorization", "Bearer " + accessToken)
                .header("Prefer", IMMUTABLE_IDS)
                .retrieve()
                .toBodilessEntity());
        return new SentEmail(messageId, conversationId);
    }

    /** Graph has no per-app revoke: the stored token goes with the row, and the one in memory goes here. */
    @Override
    public void revoke(String grantId) {
        mailboxTokens.forget(grantId);
    }

    /** Replies are found by the poll; Graph subscriptions are a later follow-up. */
    @Override
    public List<MailboxEvent> readWebhook(String signature, byte[] body) {
        return List.of();
    }

    /** Who wrote into the conversation since {@code since}: the sender's address only, never subject or body. */
    @Override
    public List<String> senderAddressesInThread(String grantId, String threadId, Instant since) {
        String accessToken = mailboxTokens.accessToken(grantId);
        String filter = "conversationId eq '" + threadId.replace("'", "''") + "'";
        JsonNode page = graphCall("thread", accessToken, client -> client.get()
                .uri(builder -> builder.path("/v1.0/me/messages")
                        .queryParam("$filter", "{filter}")
                        .queryParam("$select", "from,receivedDateTime")
                        .queryParam("$top", THREAD_PAGE)
                        .build(filter))
                .accept(MediaType.APPLICATION_JSON));
        return sendersSince(page, since);
    }

    static List<String> sendersSince(JsonNode page, Instant since) {
        List<String> senders = new ArrayList<>();
        if (page == null) {
            return senders;
        }
        for (JsonNode message : page.path("value")) {
            Instant received = instantOrNull(message.get("receivedDateTime"));
            String address = textOrNull(message.path("from").path("emailAddress").get("address"));
            if (address != null && (received == null || !received.isBefore(since))) {
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

    static Map<String, Object> messageOf(OutgoingEmail email) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("subject", email.subject());
        message.put("body", Map.of("contentType", "HTML", "content", email.htmlBody()));
        message.put("toRecipients", List.of(Map.of("emailAddress", Map.of("address", email.to()))));
        return message;
    }

    private JsonNode graphCall(String operation, String accessToken,
                               Function<RestClient, RestClient.RequestHeadersSpec<?>> request) {
        return guard.call(VendorCall.of(VENDOR, operation), () -> request.apply(graph)
                .header("Authorization", "Bearer " + accessToken)
                .header("Prefer", IMMUTABLE_IDS)
                .retrieve()
                .body(JsonNode.class));
    }

    private ProviderCredentials requireApp(UUID workspaceId) {
        return credentials.resolve(workspaceId, IntegrationProvider.MICROSOFT)
                .orElseThrow(() -> ApiException.of(ErrorCode.MAILBOX_UNAVAILABLE));
    }

    /** The calendar on our own gateway is #647's; until then a direct mailbox's meetings are not read. */
    private static ApiException calendarNotYet() {
        return ApiException.of(ErrorCode.MAILBOX_UNAVAILABLE);
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

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() || node.asString("").isBlank() ? null : node.asString("");
    }
}
