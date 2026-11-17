package app.lightmove.api.core.text.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
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
    private static final Pattern MEMBER_ID_SLUG = Pattern.compile("^acoa[a-z0-9_-]{15,}$");

    private LinkedInUrls() {}

    /** The {@code /in/<slug>} a member profile URL names, or null when it is not one. */
    public static String profileSlugOrNull(String url) {
        return slugOrNull(url, PROFILE_SLUG);
    }

    /**
     * Whether a (lower-cased) profile slug is LinkedIn's member id — {@code /in/ACoAA…}, what Sales Navigator and search
     * links open — rather than the person's vanity name. Both address one human, and only research can pair them.
     */
    public static boolean isMemberIdSlug(String slug) {
        return slug != null && MEMBER_ID_SLUG.matcher(slug).matches();
    }

    /** The canonical address of a member profile, as research and the stored profile spell it. */
    public static String profileUrlOf(String profileSlug) {
        return "https://www.linkedin.com/in/" + profileSlug + "/";
    }

    /** The {@code /company/<slug>} a company page URL names, or null when it is not one. */
    public static String companySlugOrNull(String url) {
        return slugOrNull(url, COMPANY_SLUG);
    }

    /**
     * A slug as a browser's address bar spells it, every non-ASCII byte percent-encoded and lower-cased like the URL
     * columns are compared. The slug readers decode, while the extension stores the encoded address, so a decoded
     * {@code jérôme} matched no stored {@code j%c3%a9r%c3%b4me} and the plugin offered to file someone already filed.
     */
    public static String percentEncodedSlug(String slug) {
        StringBuilder encoded = new StringBuilder();
        for (byte octet : slug.getBytes(StandardCharsets.UTF_8)) {
            if (octet >= 0) {
                encoded.append((char) octet);
            } else {
                encoded.append('%').append(String.format(Locale.ROOT, "%02x", octet & 0xff));
            }
        }
        return encoded.toString();
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
