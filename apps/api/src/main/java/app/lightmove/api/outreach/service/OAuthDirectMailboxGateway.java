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
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.ProviderTokenGrant;
import app.lightmove.api.outreach.model.ReleasedGrant;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

    private final IntegrationProvider integrationProvider;
    private final String provider;
    private final String vendor;
    private final ProviderCredentialsResolver credentials;
    protected final ProviderTokenClient tokenEndpoint;
    protected final MailboxTokens mailboxTokens;
    protected final VendorCallGuard guard;
    protected final RestClient api;

    protected OAuthDirectMailboxGateway(IntegrationProvider integrationProvider, String provider, String vendor,
                                        ProviderCredentialsResolver credentials, ProviderTokenClient tokenEndpoint,
                                        MailboxTokens mailboxTokens, VendorClientFactory clientFactory,
                                        VendorRateLimiter rateLimiter, VendorCallGuard guard, String apiBaseUrl) {
        this.integrationProvider = integrationProvider;
        this.provider = provider;
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

    /** The scopes asked of the consent screen, and of the redemption where the provider wants them repeated. */
    protected abstract List<String> scopes();

    /** The address of the mailbox {@code accessToken} was just issued for; null when the provider did not say. */
    protected abstract String mailboxAddressOf(String accessToken);

    /** The scopes a code redemption repeats; none where the provider takes them from the consent alone. */
    protected List<String> redemptionScopes() {
        return List.of();
    }

    /** Anything the provider holds for a grant beyond the access token in memory, which {@link #revoke} drops. */
    protected void release(ReleasedGrant released) {
    }

    /** What every request to the provider's API carries beyond the bearer token. */
    protected RestClient.RequestHeadersSpec<?> withProviderHeaders(RestClient.RequestHeadersSpec<?> request) {
        return request;
    }

    @Override
    public String provider() {
        return provider;
    }

    /** Offered where anyone could connect: Uncava's shared app is configured, or some firm brought its own. */
    @Override
    public boolean isOffered() {
        return credentials.isAnyAppAt(integrationProvider);
    }

    @Override
    public boolean isOfferedTo(UUID workspaceId) {
        return credentials.resolve(workspaceId, integrationProvider).isPresent();
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
    public GrantedMailbox redeem(UUID workspaceId, String provider, String code, URI redirectUri) {
        ProviderCredentials app = requireApp(workspaceId);
        VendorCall call = VendorCall.of(vendor, "redeem");
        ProviderTokenGrant token;
        try {
            token = tokenEndpoint.redeemCode(app, code, redirectUri, redemptionScopes());
        } catch (ProviderGrantRefused | ProviderAppUnavailable refused) {
            throw new VendorException(call, VendorFailureKind.BAD_REQUEST, refused);
        }
        if (token.refreshToken() == null) {
            // Without a refresh token the mailbox dies with its first access token, within the hour.
            throw new VendorException(call, VendorFailureKind.MALFORMED_RESPONSE, null);
        }
        String address = mailboxAddressOf(token.accessToken());
        if (address == null) {
            throw new VendorException(VendorCall.of(vendor, "mailbox-address"), VendorFailureKind.MALFORMED_RESPONSE,
                    null);
        }
        return new GrantedMailbox(MailboxGrants.mintDirect(this.provider), address, this.provider,
                token.refreshToken());
    }

    @Override
    public void revoke(ReleasedGrant released) {
        mailboxTokens.forget(released.grantId());
        release(released);
    }

    /** Replies are found by the poll; provider push is a later follow-up. */
    @Override
    public List<MailboxEvent> readWebhook(String signature, byte[] body) {
        return List.of();
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

    protected JsonNode apiCall(String operation, String accessToken,
                               Function<RestClient, RestClient.RequestHeadersSpec<?>> request) {
        return guard.call(VendorCall.of(vendor, operation), () -> withProviderHeaders(request.apply(api)
                .header("Authorization", "Bearer " + accessToken))
                .retrieve()
                .body(JsonNode.class));
    }

    protected VendorCall vendorCall(String operation) {
        return VendorCall.of(vendor, operation);
    }

    private ProviderCredentials requireApp(UUID workspaceId) {
        return credentials.resolve(workspaceId, integrationProvider)
                .orElseThrow(() -> ApiException.of(ErrorCode.MAILBOX_UNAVAILABLE));
    }

    /** The calendar on our own gateway is #647's; until then a direct mailbox's meetings are not read. */
    private static ApiException calendarNotYet() {
        return ApiException.of(ErrorCode.MAILBOX_CALENDAR_UNSUPPORTED);
    }

    protected static String textOrNull(JsonNode node) {
        return node == null || node.isNull() || node.asString("").isBlank() ? null : node.asString("");
    }
}
