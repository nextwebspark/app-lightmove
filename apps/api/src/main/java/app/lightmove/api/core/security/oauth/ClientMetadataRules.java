package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.security.apikey.ApiKeyScope;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/** What a client may say about itself, read alike from a registration and a metadata document. */
final class ClientMetadataRules {

    static final String INVALID_CLIENT_METADATA = "invalid_client_metadata";
    static final String INVALID_REDIRECT_URI = "invalid_redirect_uri";

    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_URI_LENGTH = 2048;
    private static final Set<String> GRANT_TYPES = Set.of(AuthorizationGrantType.AUTHORIZATION_CODE.getValue(),
            AuthorizationGrantType.REFRESH_TOKEN.getValue());
    private static final List<String> EVERY_SCOPE = ApiKeyScope.dataScopes().stream().map(ApiKeyScope::value).toList();

    private ClientMetadataRules() {
    }

    static boolean isPublicAuthMethod(String method) {
        return method == null || ClientAuthenticationMethod.NONE.getValue().equals(method);
    }

    static boolean isAllowedGrantTypes(Collection<String> grantTypes) {
        return grantTypes == null || GRANT_TYPES.containsAll(grantTypes);
    }

    static boolean isAllowedResponseTypes(Collection<String> responseTypes) {
        return responseTypes == null || responseTypes.stream().allMatch("code"::equals);
    }

    /**
     * The scopes it may ever ask for: the known ones it named, or all of them when it named none it could have. What a
     * grant carries is still only what the user ticked at consent.
     */
    static List<String> scopesOf(Collection<String> asked) {
        if (asked == null) {
            return EVERY_SCOPE;
        }
        List<String> known = EVERY_SCOPE.stream().filter(asked::contains).toList();
        return known.isEmpty() ? EVERY_SCOPE : known;
    }

    /** Its own name, or where it sends people when it gave none: the consent screen must name something. */
    static String displayNameOf(String clientName, List<String> redirectUris) {
        if (clientName != null && !clientName.isBlank()) {
            String trimmed = clientName.strip();
            return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
        }
        String host = OAuthUris.hostOf(redirectUris.getFirst());
        return host == null ? "Unnamed app" : host;
    }

    /** A logo or home page the screens fetch or link to: https only, as a provider's picture is, else dropped. */
    static String httpsUriOrNull(Object value) {
        if (!(value instanceof String uri) || uri.isBlank() || uri.length() > MAX_URI_LENGTH) {
            return null;
        }
        try {
            URI parsed = new URI(uri);
            return "https".equals(parsed.getScheme() == null ? null : parsed.getScheme().toLowerCase(Locale.ROOT))
                    && parsed.getHost() != null && parsed.getRawUserInfo() == null ? uri : null;
        } catch (URISyntaxException malformed) {
            return null;
        }
    }
}
