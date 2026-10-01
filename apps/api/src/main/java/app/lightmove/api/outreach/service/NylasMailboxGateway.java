package app.lightmove.api.outreach.service;

import app.lightmove.api.core.config.NylasSettings;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.OutgoingEmail;
import app.lightmove.api.outreach.model.SentEmail;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

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
        SendAnswer sent = guard.call(VendorCall.of(VENDOR, "send"), () -> client.post()
                .uri("/v3/grants/{grantId}/messages/send", grantId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "to", List.of(Map.of("email", email.to())),
                        "subject", email.subject(),
                        "body", email.htmlBody()))
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

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record TokenAnswer(String grantId) {}

    record GrantAnswer(Grant data) {}

    record Grant(String id, String provider, String email) {}

    record SendAnswer(SentMessage data) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record SentMessage(String id, String threadId) {}
}
