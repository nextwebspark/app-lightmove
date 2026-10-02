package app.lightmove.api.outreach.service;

import app.lightmove.api.core.config.NylasSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.outreach.model.DeliveryFailure;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.InboundMessage;
import app.lightmove.api.outreach.model.MailboxAccessWithdrawn;
import app.lightmove.api.outreach.model.MailboxEvent;
import app.lightmove.api.outreach.model.OutgoingEmail;
import app.lightmove.api.outreach.model.SentEmail;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
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
    public void revoke(String grantId) {
        guard.call(VendorCall.of(VENDOR, "revoke"), () -> client.delete()
                .uri("/v3/grants/{grantId}", grantId)
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
            default -> List.of();
        };
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
            return MessageDigest.isEqual(expected, signature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII));
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
