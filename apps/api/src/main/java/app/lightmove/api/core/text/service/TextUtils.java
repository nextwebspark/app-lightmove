package app.lightmove.api.core.text.service;

import java.net.URI;

/**
 * Normalisation for free text. An untouched input posts as {@code ""}, which stored would render as a
 * present-but-blank cell and sort ahead of real values, so "supplied but empty" becomes null once.
 */
public final class TextUtils {

    private TextUtils() {}

    /** Trimmed, or null when nothing is left. */
    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Trimmed with inner whitespace runs collapsed to one space, or null when nothing is left. */
    public static String collapseWhitespaceToNull(String value) {
        return value == null ? null : blankToNull(value.replaceAll("\\s+", " "));
    }

    /**
     * A browsable http(s) address, or null. A security boundary: {@code acme.com} as an {@code href} is
     * a relative link inside the SPA, so a bare host gains {@code https://}, and anything that is not
     * then http(s) — {@code javascript:} being the interesting case — is dropped rather than stored.
     */
    public static String browsableUrlOrNull(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        String candidate = trimmed.contains("://") ? trimmed : "https://" + trimmed;
        URI parsed;
        try {
            parsed = URI.create(candidate);
        } catch (IllegalArgumentException notAUri) {
            return null;
        }
        String scheme = parsed.getScheme();
        boolean browsable = scheme != null
                && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                && parsed.getHost() != null;
        return browsable ? candidate : null;
    }
}
