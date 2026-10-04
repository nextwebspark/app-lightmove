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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * Holds the authorisation request in a cookie while the browser is at the provider, so any instance can
 * finish the callback. <b>{@code SameSite=Lax}, never {@code Strict}</b>: Strict withholds it on the
 * provider's cross-site callback. Unsigned; the binding is the {@code state} check.
 */
@Slf4j
@Component
public class CookieAuthorizationRequestStore
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "lm_oauth_request";

    /**
     * Coupled to {@code OAuth2LoginAuthenticationFilter.DEFAULT_FILTER_PROCESSES_URI}: customising
     * {@code redirectionEndpoint().baseUri(…)} would stop the cookie arriving, and MockMvc attaches
     * cookies regardless of path, so only a browser would notice.
     */
    private static final String COOKIE_PATH = "/login/oauth2";

    private static final int FORMAT_VERSION = 1;

    private final ObjectMapper json;
    private final CookieSettings cookieSettings;
    private final Duration ttl;

    public CookieAuthorizationRequestStore(ObjectMapper json, LightMoveProperties properties) {
        this.json = json;
        this.cookieSettings = properties.auth().cookie();
        this.ttl = properties.auth().oauthRequestTtl();
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        Cookie cookie = WebUtils.getCookie(request, COOKIE_NAME);
        if (state == null || cookie == null || !StringUtils.hasText(cookie.getValue())) {
            return null;
        }
        try {
            StoredAuthorizationRequest stored = json.readValue(
                    Base64.getUrlDecoder().decode(cookie.getValue()), StoredAuthorizationRequest.class);
            if (stored == null || stored.version() != FORMAT_VERSION || !state.equals(stored.state())) {
                return null;
            }
            return stored.toAuthorizationRequest();
        } catch (RuntimeException ex) {
            // Inside the filter chain, where GlobalExceptionHandler does not exist: a throw is a raw
            // error page. Well-formed JSON of the wrong shape dies in the builder, hence inside the try.
            // Not the throwable: Jackson quotes its source, which may hold a code_verifier.
            log.debug("Discarding an unreadable OAuth authorisation cookie ({})",
                    ex.getClass().getSimpleName());
            return null;
        }
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
        // Cleared usable or not: one we refused would otherwise fail every retry until its Max-Age.
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("").maxAge(0).build().toString());
        return loadAuthorizationRequest(request);
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String value) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                // From the refresh cookie's settings: widening lightmove.auth.cookie.domain also widens
                // this cookie, which holds a code_verifier.
                .secure(cookieSettings.secure())
                .sameSite("Lax")
                .path(COOKIE_PATH);

        if (StringUtils.hasText(cookieSettings.domain())) {
            builder.domain(cookieSettings.domain());
        }
        return builder;
    }

    /**
     * The cookie's versioned payload, field by field, so a Spring Security upgrade cannot change the
     * format under a live browser. Parameters and attributes are carried whole.
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
                    requireTextValues(request.getAdditionalParameters()),
                    requireTextValues(request.getAttributes()));
        }

        /** {@code authorizationCode()} re-renders {@code authorizationRequestUri}, so it is not stored. */
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

        private static Map<String, String> requireTextValues(Map<String, Object> values) {
            Map<String, String> copy = new LinkedHashMap<>();
            values.forEach((key, value) -> {
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
