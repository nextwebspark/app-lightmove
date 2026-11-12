package app.lightmove.api.outreach.service;

import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorClientSpec;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorErrorCodes;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.model.AppKeysVerdict;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.ProviderTokenGrant;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * The authorization-code (RFC 6749 §4.1) and refresh-token (§6) grants at Google, Microsoft and Zoom. The secret
 * travels in the form body ({@code client_secret_post}) except at Zoom, which accepts HTTP Basic only. Never retried
 * here: a refusal is final, and anything else is the caller's next call to try again.
 */
public class OAuthProviderTokenClient implements ProviderTokenClient {

    static final Duration READ_TIMEOUT = Duration.ofSeconds(15);
    private static final int REQUESTS_PER_SECOND = 10;

    /** Microsoft's directory for a multi-tenant app: whichever work or school directory the user signed in at. */
    static final String MICROSOFT_ANY_ORGANISATION = "organizations";

    /** The answers that mean the grant itself is gone, not that this attempt failed. */
    static final Set<String> REFUSALS = Set.of("invalid_grant", "interaction_required");

    /** The answers that mean our app was refused, whoever's token it carried. */
    static final Set<String> APP_REFUSALS = Set.of("invalid_client", "unauthorized_client");

    /** Entra's app-only grant: it checks the client id, the secret and the directory together, and needs no user. */
    static final String MICROSOFT_APP_ONLY_SCOPE = "https://graph.microsoft.com/.default";

    /** A code no provider issued. Google and Zoom authenticate the client before they read the code. */
    static final String NO_SUCH_CODE = "uncava-key-check";

    /** Entra's rules for a directory that does not exist, or a tenant that is not one: the admin's tenant is wrong. */
    static final Set<String> ENTRA_DIRECTORY_REFUSALS = Set.of("AADSTS90002", "AADSTS900023");

    public static final Map<IntegrationProvider, String> PROVIDER_TOKEN_HOSTS = Map.of(
            IntegrationProvider.GOOGLE, "https://oauth2.googleapis.com",
            IntegrationProvider.MICROSOFT, MicrosoftMailboxGateway.LOGIN,
            IntegrationProvider.ZOOM, "https://zoom.us");

    private final Map<IntegrationProvider, RestClient> clients = new EnumMap<>(IntegrationProvider.class);
    private final VendorCallGuard guard;

    /** {@code tokenHosts}: each provider's token endpoint host, {@link #PROVIDER_TOKEN_HOSTS} outside a test. */
    public OAuthProviderTokenClient(VendorClientFactory clientFactory, VendorRateLimiter rateLimiter,
                                    VendorCallGuard guard, Map<IntegrationProvider, String> tokenHosts) {
        this.guard = guard;
        for (IntegrationProvider provider : IntegrationProvider.values()) {
            String host = tokenHosts.get(provider);
            VendorClientSpec spec = new VendorClientSpec(vendorOf(provider), host, null, null, null, null,
                    READ_TIMEOUT, REQUESTS_PER_SECOND);
            clients.put(provider, clientFactory.create(spec, RestClient.builder(), rateLimiter));
        }
    }

    @Override
    public ProviderTokenGrant refresh(ProviderCredentials credentials, String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return exchange(credentials, form, "refresh-token");
    }

    @Override
    public ProviderTokenGrant redeemCode(ProviderCredentials credentials, String code, URI redirectUri,
                                           List<String> scopes) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri.toString());
        if (!scopes.isEmpty()) {
            form.add("scope", String.join(" ", scopes));
        }
        return exchange(credentials, form, "redeem-code");
    }

    @Override
    public void revoke(IntegrationProvider provider, String token) {
        String path = revocationPathOf(provider);
        if (path == null || token == null) {
            return;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        guard.call(VendorCall.of(vendorOf(provider), "revoke"), () -> clients.get(provider).post()
                .uri(path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public AppKeysVerdict checkKeys(ProviderCredentials credentials, URI redirectUri) {
        IntegrationProvider provider = credentials.provider();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        if (provider == IntegrationProvider.MICROSOFT) {
            form.add("grant_type", "client_credentials");
            form.add("scope", MICROSOFT_APP_ONLY_SCOPE);
        } else {
            form.add("grant_type", "authorization_code");
            form.add("code", NO_SUCH_CODE);
            form.add("redirect_uri", redirectUri.toString());
        }
        VendorCall call = VendorCall.of(vendorOf(provider), "check-keys");
        return guard.call(call, () -> tokenRequest(credentials, form)
                .exchange((request, response) -> verdictOf(provider, call, response)));
    }

    private ProviderTokenGrant exchange(ProviderCredentials credentials, MultiValueMap<String, String> form,
                                          String operation) {
        VendorCall call = VendorCall.of(vendorOf(credentials.provider()), operation);
        JsonNode answer = guard.call(call, () -> tokenRequest(credentials, form)
                .retrieve()
                .onStatus(status -> status.value() == 400 || status.value() == 401,
                        (request, response) -> {
                            throw refusalOrFailure(call, response);
                        })
                .body(JsonNode.class));
        return read(call, answer);
    }

    private RestClient.RequestHeadersSpec<?> tokenRequest(ProviderCredentials credentials,
                                                         MultiValueMap<String, String> form) {
        IntegrationProvider provider = credentials.provider();
        boolean basic = provider == IntegrationProvider.ZOOM;
        if (!basic) {
            form.add("client_id", credentials.clientId());
            form.add("client_secret", credentials.clientSecret());
        }
        return clients.get(provider).post()
                .uri(tokenPathOf(provider), tenantOf(credentials))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (basic) {
                        headers.setBasicAuth(credentials.clientId(), credentials.clientSecret(),
                                StandardCharsets.UTF_8);
                    }
                })
                .body(form);
    }

    static ProviderTokenGrant read(VendorCall call, JsonNode answer) {
        String accessToken = answer == null ? null : textOrNull(answer.get("access_token"));
        if (accessToken == null) {
            throw new VendorException(call, VendorFailureKind.MALFORMED_RESPONSE, null);
        }
        JsonNode expiresIn = answer.get("expires_in");
        long seconds = expiresIn == null || !expiresIn.canConvertToLong() ? 3600 : expiresIn.asLong();
        String scope = textOrNull(answer.get("scope"));
        Set<String> scopes = scope == null ? Set.of() : Set.copyOf(List.of(scope.trim().split("\\s+")));
        return new ProviderTokenGrant(accessToken, Duration.ofSeconds(seconds),
                textOrNull(answer.get("refresh_token")), scopes);
    }

    /**
     * RFC 6749 §5.2's {@code error}, read off the body, with Entra's {@code AADSTS} rule number where it gives one
     * ({@link VendorErrorCodes}); whatever else the body says is never kept.
     */
    static RuntimeException refusalOrFailure(VendorCall call, ClientHttpResponse response) throws IOException {
        JsonNode body = VendorErrorCodes.jsonOrNull(
                response.getBody().readNBytes(VendorErrorCodes.MAX_ERROR_BODY_BYTES));
        String error = body == null ? null : textOrNull(body.get("error"));
        String code = VendorErrorCodes.codeOrNull(body);
        String named = code != null ? code : error;
        if (error != null && REFUSALS.contains(error)) {
            return new ProviderGrantRefused(named);
        }
        // A token endpoint's 401 is always about the client: it authenticates nobody else.
        if ((error != null && APP_REFUSALS.contains(error)) || response.getStatusCode().value() == 401) {
            return new ProviderAppUnavailable(named == null ? "401" : named);
        }
        HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
        return new VendorException(call, status == null ? VendorFailureKind.BAD_REQUEST
                : VendorFailureKind.of(status), code, null);
    }

    /**
     * A refused client is refused keys everywhere, and at Entra so is an unknown directory; any other Entra refusal is
     * no answer about the keys. Google and Zoom were sent a code nobody issued, and a token endpoint authenticates the
     * client before it reads the code (RFC 6749 §4.1.3), so any other refusal there means the keys were accepted.
     */
    static AppKeysVerdict verdictOf(IntegrationProvider provider, VendorCall call, ClientHttpResponse response)
            throws IOException {
        int status = response.getStatusCode().value();
        if (response.getStatusCode().is2xxSuccessful()) {
            return AppKeysVerdict.ACCEPTED;
        }
        if (status != 400 && status != 401) {
            HttpStatus known = HttpStatus.resolve(status);
            throw new VendorException(call, known == null ? VendorFailureKind.UNAVAILABLE : VendorFailureKind.of(known),
                    null);
        }
        JsonNode body = VendorErrorCodes.jsonOrNull(
                response.getBody().readNBytes(VendorErrorCodes.MAX_ERROR_BODY_BYTES));
        String error = body == null ? null : textOrNull(body.get("error"));
        String named = VendorErrorCodes.codeOrNull(body);
        AppKeysVerdict refused = AppKeysVerdict.refused(named == null ? String.valueOf(status) : named);
        boolean clientRefused = status == 401 || (error != null && APP_REFUSALS.contains(error));
        if (clientRefused || ENTRA_DIRECTORY_REFUSALS.contains(refused.refusedWith())) {
            return refused;
        }
        if (provider == IntegrationProvider.MICROSOFT) {
            throw new VendorException(call, VendorFailureKind.BAD_REQUEST, named, null);
        }
        return AppKeysVerdict.ACCEPTED;
    }

    /** A template, so the admin-typed tenant is encoded as one path segment and can never steer the request. */
    private static String tokenPathOf(IntegrationProvider provider) {
        return switch (provider) {
            case GOOGLE -> "/token";
            case MICROSOFT -> "/{tenant}/oauth2/v2.0/token";
            case ZOOM -> "/oauth/token";
        };
    }

    /** A single-tenant app signs in at its own directory; Uncava's multi-tenant one at any organisation's. */
    static String tenantOf(ProviderCredentials credentials) {
        return credentials.tenantId() == null ? MICROSOFT_ANY_ORGANISATION : credentials.tenantId();
    }

    /** RFC 7009 at Google. Microsoft has no per-app revoke; Zoom's arrives with its gateway (#648). */
    private static String revocationPathOf(IntegrationProvider provider) {
        return switch (provider) {
            case GOOGLE -> "/revoke";
            case MICROSOFT, ZOOM -> null;
        };
    }

    private static String vendorOf(IntegrationProvider provider) {
        return provider.name().toLowerCase(Locale.ROOT) + "-oauth";
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() || node.asText().isBlank() ? null : node.asText();
    }
}
