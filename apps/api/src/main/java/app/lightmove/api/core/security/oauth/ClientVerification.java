package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.config.LightMoveProperties;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Whether the consent screen may name an app as the one it says it is. A metadata document is served from its
 * client's own host, so a document on a host we list is that company's app; a dynamic registration can call itself
 * anything, so it is never verified, whatever its name.
 */
@Component
public class ClientVerification {

    private final Set<String> verifiedHosts;

    public ClientVerification(LightMoveProperties properties) {
        this.verifiedHosts = properties.mcp().verifiedClientHosts().stream()
                .map(host -> host.trim().toLowerCase(Locale.ROOT))
                .filter(host -> !host.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isVerified(OAuthClientSource source, String clientId) {
        return switch (source) {
            case SEEDED -> true;
            case DCR -> false;
            case CIMD -> {
                String host = documentHostOf(source, clientId);
                yield host != null && verifiedHosts.contains(host);
            }
        };
    }

    /** The host a metadata document was read from; nothing for any other client. */
    public static String documentHostOf(OAuthClientSource source, String clientId) {
        if (source != OAuthClientSource.CIMD) {
            return null;
        }
        try {
            String host = URI.create(clientId).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
