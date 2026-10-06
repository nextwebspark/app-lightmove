package app.lightmove.api.core.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * How the authorize endpoint answers, for its two kinds of caller. The AI client's browser navigates — it gets real
 * redirects, and is sent to the SPA's consent screen to sign in. The consent screen calls with the session's bearer
 * token — it gets the same redirect as JSON ({@code redirectUri}) to follow itself, since a fetch cannot hand a
 * cross-origin redirect to the page. Every redirect back to the client carries {@code iss} (RFC 9207), so a client
 * talking to several servers can tell which one answered.
 */
public class AuthorizationEndpointReplies
        implements AuthenticationSuccessHandler, AuthenticationFailureHandler, AuthenticationEntryPoint {

    static final String CONSENT_ROUTE = "/oauth/consent";
    private static final String ISSUER_PARAMETER = "iss";

    private final McpServerIdentity identity;
    private final JsonMapper json;
    private final AuthenticationEntryPoint bearerChallenge = new BearerTokenAuthenticationEntryPoint();

    public AuthorizationEndpointReplies(McpServerIdentity identity, JsonMapper json) {
        this.identity = identity;
        this.json = json;
    }

    /** The code, back to the client. */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthorizationCodeRequestAuthenticationToken result =
                (OAuth2AuthorizationCodeRequestAuthenticationToken) authentication;
        UriComponentsBuilder target = UriComponentsBuilder.fromUriString(result.getRedirectUri())
                .queryParam(OAuth2ParameterNames.CODE, result.getAuthorizationCode().getTokenValue());
        redirect(request, response, withStateAndIssuer(target, result.getState()));
    }

    /** A refusal: to the client where its redirect URI is proven, otherwise never — to the consent screen's error. */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        OAuth2Error error = exception instanceof OAuth2AuthenticationException oauth
                ? oauth.getError()
                : new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST);
        OAuth2AuthorizationCodeRequestAuthenticationToken original =
                exception instanceof OAuth2AuthorizationCodeRequestAuthenticationException codeRequest
                        ? codeRequest.getAuthorizationCodeRequestAuthentication()
                        : null;

        if (original != null && StringUtils.hasText(original.getRedirectUri())) {
            UriComponentsBuilder target = UriComponentsBuilder.fromUriString(original.getRedirectUri())
                    .queryParam(OAuth2ParameterNames.ERROR, error.getErrorCode());
            if (StringUtils.hasText(error.getDescription())) {
                target.queryParam(OAuth2ParameterNames.ERROR_DESCRIPTION, encode(error.getDescription()));
            }
            redirect(request, response, withStateAndIssuer(target, original.getState()));
            return;
        }

        if (isFromConsentScreen(request)) {
            Map<String, String> body = new LinkedHashMap<>();
            body.put(OAuth2ParameterNames.ERROR, error.getErrorCode());
            body.put(OAuth2ParameterNames.ERROR_DESCRIPTION, error.getDescription());
            writeJson(response, HttpStatus.BAD_REQUEST, body);
            return;
        }
        response.sendRedirect(UriComponentsBuilder.fromUriString(identity.issuer() + CONSENT_ROUTE)
                .queryParam(OAuth2ParameterNames.ERROR, encode(error.getErrorCode()))
                .build(true)
                .toUriString());
    }

    /**
     * Nobody signed in. A navigation is the AI client's browser arriving: it goes to the consent screen with the
     * request as it came, and signs in there. A call from the consent screen whose token has lapsed is a 401.
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, jakarta.servlet.ServletException {
        if (isFromConsentScreen(request) || !HttpMethod.GET.matches(request.getMethod())) {
            bearerChallenge.commence(request, response, authException);
            return;
        }
        String query = request.getQueryString();
        response.sendRedirect(identity.issuer() + CONSENT_ROUTE + (query == null ? "" : "?" + query));
    }

    static boolean isFromConsentScreen(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.AUTHORIZATION) != null;
    }

    private String withStateAndIssuer(UriComponentsBuilder target, String state) {
        if (StringUtils.hasText(state)) {
            target.queryParam(OAuth2ParameterNames.STATE, encode(state));
        }
        // build(true): every component above is already encoded.
        return target.queryParam(ISSUER_PARAMETER, encode(identity.issuer())).build(true).toUriString();
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response, String target) throws IOException {
        if (isFromConsentScreen(request)) {
            writeJson(response, HttpStatus.OK, Map.of("redirectUri", target));
            return;
        }
        response.sendRedirect(target);
    }

    private void writeJson(HttpServletResponse response, HttpStatus status, Map<String, ?> body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getWriter().write(json.writeValueAsString(body));
    }

    private static String encode(String value) {
        return UriUtils.encode(value, StandardCharsets.UTF_8);
    }
}
