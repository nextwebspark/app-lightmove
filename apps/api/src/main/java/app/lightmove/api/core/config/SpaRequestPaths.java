package app.lightmove.api.core.config;

import java.util.List;

/**
 * Which request paths belong to the SPA: everything but the API, Actuator, Spring's OAuth2
 * sign-in endpoints and the OAuth metadata documents. {@code SecurityConfig.spaChain} and {@link CanonicalOriginRedirectFilter} must agree on
 * this, so both read it here; the rationale for matching by exclusion lives on {@code spaChain}.
 */
public final class SpaRequestPaths {

    private static final List<String> NON_SPA_PREFIXES = List.of("/api/", "/actuator", "/oauth2/", "/login/oauth2",
            // RFC 8414 / RFC 9728 metadata, served by the MCP authorization server and resource.
            "/.well-known/oauth-");

    private SpaRequestPaths() {}

    public static boolean isSpaPath(String requestUri) {
        return NON_SPA_PREFIXES.stream().noneMatch(requestUri::startsWith);
    }
}
