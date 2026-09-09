package app.lightmove.api.auth;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.web.util.UriComponentsBuilder;

/** Shared by the two suites that drive a provider redirect. */
final class OAuthFlowSupport {

    /** The cookie carrying the authorisation request. */
    static final String AUTHORIZATION_REQUEST_COOKIE = "lm_oauth_request";

    private OAuthFlowSupport() {
    }

    /**
     * The {@code state} the redirect carried, percent-decoded: the query is encoded and the store
     * compares the raw value, so a state ending in %3D would simply never match.
     */
    static String stateOf(String authorizationUri) {
        String state = UriComponentsBuilder.fromUriString(authorizationUri)
                .build()
                .getQueryParams()
                .getFirst("state");
        return URLDecoder.decode(state, StandardCharsets.UTF_8);
    }
}
