package app.lightmove.api.core.text.service;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LinkedIn URL anatomy, in one place.
 *
 * <p>Every caller that spends money on a URL asks the same two questions — is this really a LinkedIn
 * page, and which slug does it name — and the four hand-rolled parsers this replaced answered them
 * differently. One checked the host and one did not, so a mis-scraped {@code example.com/company/x}
 * bought a vendor lookup that the person path would have refused for free. Both answers now come
 * from here, and "worth billing" is exactly "a slug came back".
 *
 * <p>Slugs are lowercased because that is how the vendor datasets key them and their filters match
 * exactly, while LinkedIn itself treats the path case-insensitively: {@code /in/John-Smith} and
 * {@code /in/john-smith} are one profile and must be one lookup.
 */
public final class LinkedInUrls {

    private static final Pattern PROFILE_SLUG = Pattern.compile("^/in/([^/?#]+)");
    private static final Pattern COMPANY_SLUG = Pattern.compile("^/company/([^/?#]+)");

    private LinkedInUrls() {}

    /** The {@code /in/<slug>} a member profile URL names, or null when it is not one. */
    public static String profileSlugOrNull(String url) {
        return slugOrNull(url, PROFILE_SLUG);
    }

    /** The {@code /company/<slug>} a company page URL names, or null when it is not one. */
    public static String companySlugOrNull(String url) {
        return slugOrNull(url, COMPANY_SLUG);
    }

    private static String slugOrNull(String url, Pattern pattern) {
        if (url == null || url.isBlank()) {
            return null;
        }
        URI parsed;
        try {
            parsed = URI.create(url.trim());
        } catch (IllegalArgumentException notAUri) {
            return null;
        }
        String host = parsed.getHost();
        if (host == null || parsed.getPath() == null) {
            return null;
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        boolean linkedIn = lowerHost.equals("linkedin.com") || lowerHost.endsWith(".linkedin.com");
        if (!linkedIn) {
            return null;
        }
        Matcher slug = pattern.matcher(parsed.getPath());
        return slug.find() ? slug.group(1).toLowerCase(Locale.ROOT) : null;
    }
}
