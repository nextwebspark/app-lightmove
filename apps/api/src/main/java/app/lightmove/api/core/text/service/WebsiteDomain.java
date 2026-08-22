package app.lightmove.api.core.text.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * The registrable domain inside a website URL — {@code https://www.acwapower.com/en} is
 * {@code acwapower.com}.
 *
 * <p>Parsed rather than pattern-matched. Stripping the scheme by regex and keeping everything up to
 * the first slash also keeps the port and the userinfo, so {@code https://acwapower.com:8080/} stored
 * as {@code acwapower.com:8080} and {@code https://user@host.com} kept the user. {@link URI} knows
 * where the host ends; a value it cannot parse is dropped rather than stored raw, because a domain
 * column holding a URL is worse than one holding nothing.
 */
public final class WebsiteDomain {

    private WebsiteDomain() {
    }

    public static String of(String website) {
        if (website == null || website.isBlank()) {
            return null;
        }
        String trimmed = website.trim();
        // A bare host parses as a path, not an authority, so URI finds no host without a scheme.
        String absolute = trimmed.contains("://") ? trimmed : "https://" + trimmed;
        try {
            String host = new URI(absolute).getHost();
            if (host == null) {
                return null;
            }
            String bare = host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            return bare.contains(".") ? bare : null;
        } catch (URISyntaxException notAUrl) {
            return null;
        }
    }
}
