package app.lightmove.api.core.text.service;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LinkedIn URL anatomy in one place: is it really LinkedIn, and which slug — "worth billing" is
 * exactly "a slug came back". Slugs are lowercased, as the vendor datasets key them.
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
