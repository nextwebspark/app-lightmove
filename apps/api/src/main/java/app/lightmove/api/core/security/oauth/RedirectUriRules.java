package app.lightmove.api.core.security.oauth;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Where a code may be sent: an https address exactly, or a listener on this machine (127.0.0.1, [::1], localhost) on any
 * port (RFC 8252). Never a private-use scheme such as {@code cursor://}, which any installed app can claim.
 */
final class RedirectUriRules {

    private static final int MAX_URIS = 10;
    private static final int MAX_LENGTH = 2048;
    private static final Set<String> LOOPBACK_HOSTS = Set.of("127.0.0.1", "[::1]", "::1", "localhost");

    private RedirectUriRules() {
    }

    /** What a registration or a metadata document may list. */
    static boolean acceptable(List<String> uris) {
        return uris != null && !uris.isEmpty() && uris.size() <= MAX_URIS
                && uris.stream().allMatch(RedirectUriRules::acceptable);
    }

    static boolean acceptable(String uri) {
        if (uri == null || uri.isBlank() || uri.length() > MAX_LENGTH || uri.contains("#") || uri.contains("*")
                || uri.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        URI parsed;
        try {
            parsed = new URI(uri);
        } catch (URISyntaxException malformed) {
            return false;
        }
        if (!parsed.isAbsolute() || parsed.getRawUserInfo() != null || parsed.getHost() == null) {
            return false;
        }
        String scheme = parsed.getScheme().toLowerCase(Locale.ROOT);
        return "https".equals(scheme) || ("http".equals(scheme) && isLoopback(parsed.getHost()));
    }

    /** Whether the authorize request's redirect is one the client registered; only a loopback one may change port. */
    static boolean matches(Collection<String> registered, String requested) {
        if (requested == null || requested.contains("#")) {
            return false;
        }
        if (registered.contains(requested)) {
            return true;
        }
        UriComponents asked;
        try {
            asked = UriComponentsBuilder.fromUriString(requested).build();
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        if (!"http".equalsIgnoreCase(asked.getScheme()) || !isLoopback(asked.getHost())) {
            return false;
        }
        String askedUri = asked.toUriString();
        return registered.stream().anyMatch(uri -> {
            try {
                return UriComponentsBuilder.fromUriString(uri).port(asked.getPort()).build().toUriString()
                        .equals(askedUri);
            } catch (IllegalArgumentException malformed) {
                return false;
            }
        });
    }

    static boolean isLoopback(String host) {
        return host != null && LOOPBACK_HOSTS.contains(host.toLowerCase(Locale.ROOT));
    }
}
