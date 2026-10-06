package app.lightmove.api.core.security.oauth;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * A client id metadata document: the client describing itself at the https URL that is its {@code client_id}, so it
 * connects with no registration. Held to what a registration may say ({@link ClientMetadataRules}), and to naming
 * itself — a document whose {@code client_id} is not the URL it was read from is someone else's, and refused.
 */
record ClientMetadataDocument(String clientId, String clientName, String clientUri, String logoUri,
                              List<String> redirectUris, List<String> scopes) {

    private static final int MAX_URL_LENGTH = 2048;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    /** Whether a client id is to be read as a metadata document's URL at all. */
    static boolean isDocumentUrl(String clientId) {
        return clientId != null && clientId.regionMatches(true, 0, "https://", 0, "https://".length());
    }

    /** An https URL with a path and nothing that could make two spellings name one document. */
    static boolean isAcceptableUrl(String clientId) {
        if (clientId.length() > MAX_URL_LENGTH) {
            return false;
        }
        try {
            URI url = new URI(clientId);
            String path = url.getRawPath();
            return "https".equals(url.getScheme().toLowerCase(Locale.ROOT)) && url.getHost() != null
                    && url.getRawUserInfo() == null && url.getRawFragment() == null
                    && path != null && path.length() > 1
                    && Arrays.stream(path.split("/")).noneMatch(segment -> segment.equals(".") || segment.equals(".."));
        } catch (URISyntaxException malformed) {
            return false;
        }
    }

    static ClientMetadataDocument read(String documentUrl, String body) {
        Map<String, Object> claims;
        try {
            claims = JSON.readValue(body, MAP);
        } catch (JacksonException notJson) {
            throw invalid("not a JSON object");
        }
        if (claims == null) {
            throw invalid("not a JSON object");
        }
        if (!documentUrl.equals(claims.get("client_id"))) {
            throw invalid("client_id does not name the URL it was read from");
        }
        Object method = claims.get("token_endpoint_auth_method");
        if (!(method == null || method instanceof String) || !ClientMetadataRules.isPublicAuthMethod((String) method)) {
            throw invalid("token_endpoint_auth_method must be none");
        }
        if (claims.containsKey("client_secret") || claims.containsKey("jwks") || claims.containsKey("jwks_uri")) {
            throw invalid("a public client publishes no secret and no keys");
        }
        if (!ClientMetadataRules.isAllowedGrantTypes(stringsOrNull(claims, "grant_types"))
                || !ClientMetadataRules.isAllowedResponseTypes(stringsOrNull(claims, "response_types"))) {
            throw invalid("only the authorization_code and refresh_token grants are allowed");
        }
        List<String> redirectUris = stringsOrNull(claims, "redirect_uris");
        if (!RedirectUriRules.acceptable(redirectUris)) {
            throw invalid("redirect_uris must be https, or http on 127.0.0.1, [::1] or localhost");
        }
        Object name = claims.get("client_name");
        Object scope = claims.get("scope");
        return new ClientMetadataDocument(documentUrl,
                ClientMetadataRules.displayNameOf(name instanceof String text ? text : null, redirectUris),
                ClientMetadataRules.httpsUriOrNull(claims.get("client_uri")),
                ClientMetadataRules.httpsUriOrNull(claims.get("logo_uri")),
                List.copyOf(redirectUris),
                ClientMetadataRules.scopesOf(scope instanceof String text ? List.of(text.trim().split("\\s+")) : null));
    }

    private static List<String> stringsOrNull(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?> items) || !items.stream().allMatch(String.class::isInstance)) {
            throw invalid(name + " must be a list of strings");
        }
        return items.stream().map(String.class::cast).toList();
    }

    private static ClientMetadataUnavailable invalid(String detail) {
        return new ClientMetadataUnavailable(ClientMetadataRefusal.INVALID_DOCUMENT, detail);
    }
}
