package app.lightmove.api.core.security.service;

import app.lightmove.api.core.config.CookieSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Where the authorisation request waits while the browser is at the provider — a cookie, not a session.
 *
 * <p>Spring's default keeps the {@code state}, the PKCE {@code code_verifier} and the {@code nonce} in
 * the servlet {@code HttpSession}: in memory, on one instance. Cloud Run runs us with no session
 * affinity, so a sign-in that starts on one instance and returns to another finds nothing and dies as
 * {@code authorization_request_not_found} — reported to the user as a failed sign-in, for a reason
 * that is entirely ours. The same happens when the API restarts with the consent screen open. Carrying
 * the request in the browser instead makes every instance able to finish any callback.
 *
 * <p><b>{@code SameSite=Lax}, never {@code Strict}.</b> The callback is a top-level cross-site GET that
 * the provider initiates, and {@code Strict} withholds a cookie on exactly that navigation — which
 * would reproduce the bug this class exists to remove. It follows that a registration configured for
 * {@code response_mode=form_post} would break it too: Lax is sent on a cross-site navigation only for
 * safe methods, and a form_post callback is a POST. Nothing requests that response mode today.
 *
 * <p><b>The value is not signed.</b> Nothing in it is a capability: {@code state}, {@code code_verifier}
 * and {@code nonce} are values we generated for this one browser, worth nothing without an
 * authorisation code the provider issued against our client id and which is redeemed server-to-server
 * with our secret. The integrity that matters is that the request came back paired with the
 * {@code state} the callback carries, which {@link #loadAuthorizationRequest} checks exactly as the
 * session-backed repository does. A signature would not defend against login CSRF either — there the
 * attacker plants a cookie our own server minted for their own real flow — so it would buy a signing
 * key and a key-rotation failure mode for nothing.
 *
 * <p>Named {@code …Store} rather than {@code …Repository} because in this codebase that suffix means
 * Spring Data; the interface says which Spring Security seam this is. Not to be confused with the SPA's
 * {@code lm_oauth_popup}, which is the popup handshake's own correlation id (see {@code oauthPopup.ts}).
 */
@Slf4j
@Component
public class CookieAuthorizationRequestStore
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "lm_oauth_request";

    /**
     * The tightest scope that still reaches Spring's redirection endpoint, so a cookie holding a
     * verifier is on the wire only where it is redeemed. It is coupled to
     * {@code OAuth2LoginAuthenticationFilter.DEFAULT_FILTER_PROCESSES_URI}: customising
     * {@code redirectionEndpoint().baseUri(…)} would stop the cookie arriving, and MockMvc attaches
     * cookies regardless of path, so only a browser would ever notice.
     */
    private static final String COOKIE_PATH = "/login/oauth2";

    private static final int FORMAT_VERSION = 1;

    private final ObjectMapper json;
    private final CookieSettings cookies;
    private final Duration ttl;

    public CookieAuthorizationRequestStore(ObjectMapper json, LightMoveProperties properties) {
        this.json = json;
        this.cookies = properties.auth().cookie();
        this.ttl = properties.auth().oauthRequestTtl();
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null) {
            return null;
        }
        return decode(request)
                .filter(stored -> state.equals(stored.state()))
                .map(StoredAuthorizationRequest::toAuthorizationRequest)
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }
        byte[] document = json.writeValueAsBytes(StoredAuthorizationRequest.of(authorizationRequest));
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(document);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(value).maxAge(ttl).build().toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        if (WebUtils.getCookie(request, COOKIE_NAME) == null) {
            return null;
        }
        // Cleared whenever one arrived, whether or not it turns out to be usable: it is single-use, and
        // one we refused would otherwise sit in the browser failing every retry until its Max-Age ran
        // out. Spring calls this once, before either handler, so this covers success and failure alike.
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("").maxAge(0).build().toString());
        return loadAuthorizationRequest(request);
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String value) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                // Both from the refresh cookie's settings, so this codebase answers "is this deployment
                // on TLS" once — local and e2e run on plain http and turn it off there.
                .secure(cookies.secure())
                .sameSite("Lax")
                .path(COOKIE_PATH);

        if (StringUtils.hasText(cookies.domain())) {
            builder.domain(cookies.domain());
        }
        return builder;
    }

    private Optional<StoredAuthorizationRequest> decode(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, COOKIE_NAME);
        if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
            return Optional.empty();
        }
        try {
            StoredAuthorizationRequest stored = json.readValue(
                    Base64.getUrlDecoder().decode(cookie.getValue()), StoredAuthorizationRequest.class);
            return stored.version() == FORMAT_VERSION ? Optional.of(stored) : Optional.empty();
        } catch (IllegalArgumentException | JacksonException ex) {
            // These handlers run inside the filter chain, where GlobalExceptionHandler does not exist,
            // so anything thrown here reaches the user as a raw container error page. A cookie we
            // cannot read is simply a request that is not there, which is what a null already means.
            log.debug("Discarding an unreadable OAuth authorisation cookie", ex);
            return Optional.empty();
        }
    }

    /**
     * The cookie's payload — written out field by field and versioned, rather than serialising
     * {@link OAuth2AuthorizationRequest} itself, so the format is something this codebase decides
     * rather than something a Spring Security upgrade can change underneath a live browser.
     *
     * <p>{@code parameters} and {@code attributes} are carried whole rather than as named fields, and
     * that is what makes {@link ProviderQuirkAwareRequestResolver} free: a registration listed in
     * {@code pkce-unsupported-registrations} simply has no {@code code_verifier} key, and the absence
     * round-trips with no list of keys here to keep in step with the list of keys there.
     */
    record StoredAuthorizationRequest(
            int version,
            String authorizationUri,
            String clientId,
            String redirectUri,
            List<String> scopes,
            String state,
            Map<String, String> parameters,
            Map<String, String> attributes) {

        static StoredAuthorizationRequest of(OAuth2AuthorizationRequest request) {
            return new StoredAuthorizationRequest(FORMAT_VERSION,
                    request.getAuthorizationUri(),
                    request.getClientId(),
                    request.getRedirectUri(),
                    List.copyOf(request.getScopes()),
                    request.getState(),
                    text(request.getAdditionalParameters()),
                    text(request.getAttributes()));
        }

        /**
         * Rebuilt with {@code authorizationCode()} rather than {@code from()} — it fixes the grant and
         * response type and re-renders {@code authorizationRequestUri} from these fields, which is why
         * that URI is not stored: the browser followed it before the cookie was ever read back.
         */
        OAuth2AuthorizationRequest toAuthorizationRequest() {
            return OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(authorizationUri)
                    .clientId(clientId)
                    .redirectUri(redirectUri)
                    .scopes(new LinkedHashSet<>(scopes))
                    .state(state)
                    .additionalParameters(new LinkedHashMap<>(parameters))
                    .attributes(new LinkedHashMap<>(attributes))
                    .build();
        }

        private static Map<String, String> text(Map<String, Object> values) {
            Map<String, String> copy = new LinkedHashMap<>();
            values.forEach((key, value) -> {
                // A cookie carries text, and everything Spring puts in these two maps already is text.
                // Anything else must break here, naming the key, rather than round-trip as its
                // toString() and be quietly wrong at the token exchange weeks later.
                if (!(value instanceof String carried)) {
                    throw new IllegalStateException(
                            "OAuth authorisation request carries a non-text value for '" + key + "'");
                }
                copy.put(key, carried);
            });
            return copy;
        }
    }
}
