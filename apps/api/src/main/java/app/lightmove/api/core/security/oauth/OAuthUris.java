package app.lightmove.api.core.security.oauth;

import java.net.URI;
import java.util.Locale;

/** Reading a host out of a URI a client supplied, which may be malformed. */
final class OAuthUris {

    private OAuthUris() {
    }

    /** Lower-cased, or null when there is none to read. */
    static String hostOf(String uri) {
        try {
            String host = URI.create(uri).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException | NullPointerException malformed) {
            return null;
        }
    }
}
