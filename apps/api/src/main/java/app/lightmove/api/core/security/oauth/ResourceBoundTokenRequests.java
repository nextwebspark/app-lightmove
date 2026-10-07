package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.config.McpSettings;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationConverter;

/**
 * Every token request must name the MCP resource (RFC 8707), as the authorization request did: a client cannot spend
 * a code or a refresh token on a token for anything else. Wraps each of the token endpoint's converters.
 */
public final class ResourceBoundTokenRequests {

    private ResourceBoundTokenRequests() {
    }

    public static void requireResource(List<AuthenticationConverter> converters, McpServerIdentity identity) {
        converters.replaceAll(converter -> request -> {
            Authentication converted = converter.convert(request);
            if (converted != null && !namesResource(request, identity.resourceUrl())) {
                throw new OAuth2AuthenticationException(new OAuth2Error(McpAuthorizationRules.INVALID_TARGET,
                        "resource must name the Uncava MCP server", null));
            }
            return converted;
        });
    }

    private static boolean namesResource(HttpServletRequest request, String resourceUrl) {
        String[] values = request.getParameterValues(McpAuthorizationRules.RESOURCE_PARAMETER);
        return values != null && values.length == 1
                && McpSettings.stripTrailingSlash(resourceUrl).equals(McpSettings.stripTrailingSlash(values[0]));
    }
}
