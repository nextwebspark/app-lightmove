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
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.MailboxEvent;
import app.lightmove.api.outreach.model.MailboxGrants;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.ProviderTokenGrant;
import app.lightmove.api.outreach.model.ReleasedGrant;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * A {@link DirectMailboxGateway} signed in through the OAuth app the workspace chose: the app's resolution, the consent
 * screen's shared parameters, the code's redemption and the provider API's client. A provider supplies its consent
 * endpoint, how it reads the mailbox's address, and the mail itself.
 */
public abstract class OAuthDirectMailboxGateway implements DirectMailboxGateway {

    /** A thread an outreach run writes into is a handful of messages. */
    static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final int REQUESTS_PER_SECOND = 10;

    /** A full diary either side of today; a calendar past it is read no further. */
    static final int MAX_EVENT_PAGES = 10;

    private final IntegrationProvider integrationProvider;
    private final String providerName;
    private final String vendor;
    private final ProviderCredentialsResolver credentials;
    private final ProviderTokenClient tokenEndpoint;
    private final MailboxTokens mailboxTokens;
    private final VendorCallGuard guard;
    private final RestClient api;

    protected OAuthDirectMailboxGateway(IntegrationProvider integrationProvider, String vendor,
                                        ProviderCredentialsResolver credentials, ProviderTokenClient tokenEndpoint,
                                        MailboxTokens mailboxTokens, VendorClientFactory clientFactory,
                                        VendorRateLimiter rateLimiter, VendorCallGuard guard, String apiBaseUrl) {
        this.integrationProvider = integrationProvider;
        this.providerName = integrationProvider.name().toLowerCase(Locale.ROOT);
        this.vendor = vendor;
        this.credentials = credentials;
        this.tokenEndpoint = tokenEndpoint;
        this.mailboxTokens = mailboxTokens;
        this.guard = guard;
        this.api = clientFactory.create(new VendorClientSpec(vendor, apiBaseUrl, null, null, null, null,
                READ_TIMEOUT, REQUESTS_PER_SECOND), RestClient.builder(), rateLimiter);
    }

    /** The consent screen's address for {@code app}, with whatever parameters only this provider asks. */
    protected abstract UriComponentsBuilder consentEndpoint(ProviderCredentials app);

    /** The scopes asked of the consent screen. */
    protected abstract List<String> scopes();

    /** The address of the mailbox {@code accessToken} was just issued for; null when the provider did not say. */
    protected abstract String mailboxAddressOf(String accessToken);

    /** Whether a code redemption repeats {@link #scopes()}; Google takes them from the consent alone. */
    protected boolean repeatsScopesAtRedemption() {
        return false;
    }

    /** Anything the provider holds for a grant beyond the access token in memory, which {@link #revoke} drops. */
    protected void release(ReleasedGrant released) {
    }

    /** What every request to the provider's API carries beyond the bearer token. */
    protected RestClient.RequestHeadersSpec<?> withProviderHeaders(RestClient.RequestHeadersSpec<?> request) {
        return request;
    }

    @Override
    public final String provider() {
        return providerName;
    }

    /** Offered where anyone could connect: Uncava's shared app is configured, or some firm brought its own. */
    @Override
    public final boolean isOffered() {
        return credentials.isAnyAppAt(integrationProvider);
    }

    @Override
    public final boolean isOfferedTo(UUID workspaceId) {
        return credentials.resolve(workspaceId, integrationProvider).isPresent();
    }

    /** Our app is chosen per workspace, so a sign-in that names none cannot be started here. */
    @Override
    public final URI authorizationUri(String provider, String loginHint, String state, URI redirectUri) {
        throw ApiException.of(ErrorCode.MAILBOX_UNAVAILABLE);
    }

    @Override
    public final GrantedMailbox redeem(String code, URI redirectUri) {
        throw ApiException.of(ErrorCode.MAILBOX_UNAVAILABLE);
    }

    @Override
    public final URI authorizationUri(UUID workspaceId, String provider, String loginHint, String state,
                                      URI redirectUri) {
        ProviderCredentials app = requireApp(workspaceId);
        UriComponentsBuilder uri = consentEndpoint(app)
                .queryParam("client_id", app.clientId())
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", redirectUri.toString())
                .queryParam("scope", String.join(" ", scopes()))
                .queryParam("state", state);
        if (loginHint != null && !loginHint.isBlank()) {
            uri.queryParam("login_hint", loginHint);
        }
        return uri.encode().build().toUri();
    }

    /** Never retried: a code is single-use. Any refusal here reads to the caller as the connect having failed. */
    @Override
    public final GrantedMailbox redeem(UUID workspaceId, String provider, String code, URI redirectUri) {
        ProviderCredentials app = requireApp(workspaceId);
        VendorCall call = vendorCall("redeem");
        ProviderTokenGrant token;
        try {
            token = tokenEndpoint.redeemCode(app, code, redirectUri,
                    repeatsScopesAtRedemption() ? scopes() : List.of());
        } catch (ProviderGrantRefused | ProviderAppUnavailable refused) {
            throw new VendorException(call, VendorFailureKind.BAD_REQUEST, refused);
        }
        if (token.refreshToken() == null) {
            // Without a refresh token the mailbox dies with its first access token, within the hour.
            throw new VendorException(call, VendorFailureKind.MALFORMED_RESPONSE, null);
        }
        String address = mailboxAddressOf(token.accessToken());
        if (address == null) {
            throw new VendorException(vendorCall("mailbox-address"), VendorFailureKind.MALFORMED_RESPONSE, null);
        }
        return new GrantedMailbox(MailboxGrants.mintDirect(providerName), address, providerName, token.refreshToken());
    }

    @Override
    public final void revoke(ReleasedGrant released) {
        mailboxTokens.forget(released.grantId());
        release(released);
    }

    /** Replies are found by the poll; provider push is a later follow-up. */
    @Override
    public List<MailboxEvent> readWebhook(String signature, byte[] body) {
        return List.of();
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

    protected JsonNode apiCall(String operation, String accessToken,
                               Function<RestClient, RestClient.RequestHeadersSpec<?>> request) {
        return guard.call(vendorCall(operation), () -> authorised(request, accessToken)
                .retrieve()
                .body(JsonNode.class));
    }

    /** {@link #apiCall} for a request the provider answers with no body. */
    protected void apiExchange(String operation, String accessToken,
                               Function<RestClient, RestClient.RequestHeadersSpec<?>> request) {
        guard.call(vendorCall(operation), () -> authorised(request, accessToken)
                .retrieve()
                .toBodilessEntity());
    }

    protected String accessTokenOf(String grantId) {
        return mailboxTokens.accessToken(grantId);
    }

    /** Withdraws the whole grant behind {@code refreshToken} at the provider's revocation endpoint. */
    protected void revokeRefreshToken(String refreshToken) {
        tokenEndpoint.revoke(integrationProvider, refreshToken);
    }

    protected VendorCall vendorCall(String operation) {
        return VendorCall.of(vendor, operation);
    }

    private RestClient.RequestHeadersSpec<?> authorised(Function<RestClient, RestClient.RequestHeadersSpec<?>> request,
                                                         String accessToken) {
        return withProviderHeaders(request.apply(api).header("Authorization", "Bearer " + accessToken));
    }

    private ProviderCredentials requireApp(UUID workspaceId) {
        return credentials.resolve(workspaceId, integrationProvider)
                .orElseThrow(() -> ApiException.of(ErrorCode.MAILBOX_UNAVAILABLE));
    }

    /** A free/busy answer with a calendar the provider could not read: never free time. */
    protected VendorException unreadableCalendar() {
        return new VendorException(vendorCall("free-busy"), VendorFailureKind.UNAVAILABLE, null);
    }

    protected static String textOrNull(JsonNode node) {
        return node == null || node.isNull() || node.asString("").isBlank() ? null : node.asString("");
    }
}
