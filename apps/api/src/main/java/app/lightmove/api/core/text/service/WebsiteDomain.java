package app.lightmove.api.core.text.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * The registrable domain inside a website URL, parsed with {@link URI} rather than a regex that kept
 * ports and userinfo. An unparseable value is dropped, never stored raw.
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
