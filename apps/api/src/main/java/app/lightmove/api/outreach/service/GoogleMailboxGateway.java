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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    static final String PROVIDER = "google";
    static final String VENDOR = "gmail";
    public static final String API = "https://www.googleapis.com";
    public static final String ACCOUNTS = "https://accounts.google.com";

    /** {@code calendar.*} now, so the calendar (#647) needs no reconnect; together they cover Recall's read too. */
    static final List<String> SCOPES = List.of("openid", "email", "https://www.googleapis.com/auth/gmail.send",
            "https://www.googleapis.com/auth/gmail.metadata", "https://www.googleapis.com/auth/calendar.events",
            "https://www.googleapis.com/auth/calendar.freebusy");

    private final String accountsBaseUrl;

    public GoogleMailboxGateway(ProviderCredentialsResolver credentials, ProviderTokenClient tokenEndpoint,
                                MailboxTokens mailboxTokens, VendorClientFactory clientFactory,
                                VendorRateLimiter rateLimiter, VendorCallGuard guard, String apiBaseUrl,
                                String accountsBaseUrl) {
        super(IntegrationProvider.GOOGLE, PROVIDER, VENDOR, credentials, tokenEndpoint, mailboxTokens, clientFactory,
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

    /** Google revokes by the refresh token, which the caller read before the row let it go; none means drop it. */
    @Override
    protected void release(ReleasedGrant released) {
        if (released.refreshToken() != null) {
            tokenEndpoint.revoke(IntegrationProvider.GOOGLE, released.refreshToken());
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
