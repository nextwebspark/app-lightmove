package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.config.LightMoveProperties;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Whether the consent screen may name an app as the one it says it is: a metadata document under a URL prefix we list,
 * never a host alone — other paths on the same host may serve what its users upload. A registration never is.
 */
@Component
public class ClientVerification {

    private final List<String> verifiedPrefixes;

    public ClientVerification(LightMoveProperties properties) {
        this.verifiedPrefixes = properties.mcp().verifiedClientIdPrefixes().stream()
                .map(String::trim)
                .filter(prefix -> !prefix.isEmpty())
                .toList();
        verifiedPrefixes.forEach(ClientVerification::requireDocumentPrefix);
    }

    public boolean isVerified(OAuthClientSource source, String clientId) {
        return switch (source) {
            case SEEDED -> true;
            case DCR -> false;
            case CIMD -> verifiedPrefixes.stream().anyMatch(clientId::startsWith);
        };
    }

    /** The host a metadata document was read from; nothing for any other client. */
    public static String documentHostOf(OAuthClientSource source, String clientId) {
        return source == OAuthClientSource.CIMD ? OAuthUris.hostOf(clientId) : null;
    }

    /** An https URL whose path ends in a slash, so a prefix can never stop part-way through a host or a segment. */
    private static void requireDocumentPrefix(String prefix) {
        if (!prefix.startsWith("https://") || OAuthUris.hostOf(prefix) == null
                || prefix.indexOf('/', "https://".length()) < 0 || !prefix.endsWith("/")) {
            throw new IllegalStateException("lightmove.mcp.verified-client-id-prefixes: not an https URL ending in a"
                    + " path and a slash: " + prefix);
        }
    }
}
