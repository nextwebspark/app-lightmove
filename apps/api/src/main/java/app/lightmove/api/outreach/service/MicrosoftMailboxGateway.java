package app.lightmove.api.outreach.service;

import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.model.OutgoingEmail;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.ReleasedGrant;
import app.lightmove.api.outreach.model.SentEmail;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
public class MicrosoftMailboxGateway extends OAuthDirectMailboxGateway {

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

    /** Ids are asked immutable, so the ones a follow-up and the poll key on survive the move to Sent Items. */
    @Override
    protected RestClient.RequestHeadersSpec<?> withProviderHeaders(RestClient.RequestHeadersSpec<?> request) {
        return request.header("Prefer", IMMUTABLE_IDS);
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
