package app.lightmove.api.core.text.service;

import java.net.URI;

/**
 * Normalisation for text a client supplied directly — a typed-in field or a value the plugin scraped —
 * where the server has nothing to resolve it against. "Supplied but empty" becomes null once, rather
 * than rendering as a blank cell that sorts ahead of real values.
 */
public final class SuppliedText {

    private SuppliedText() {}

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
