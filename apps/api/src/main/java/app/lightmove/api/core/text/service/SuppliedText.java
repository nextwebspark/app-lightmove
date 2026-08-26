package app.lightmove.api.core.text.service;

import java.net.URI;

/**
 * Normalisation for text a <i>client</i> supplied directly, where the server has no source of truth to
 * resolve the value against — a typed-in form field, or a value the browser plugin scraped off a page.
 *
 * <p>Both rules exist because of what happens downstream if they do not. An untouched text input posts
 * as {@code ""}, and an empty string stored in a snapshot column renders as a present-but-blank cell
 * and sorts ahead of real values, so "supplied but empty" has to become null once rather than at each
 * place that later asks whether a field is known.
 *
 * <p>The URL rule is a security boundary as much as a convenience. A consultant types {@code acme.com},
 * which as an {@code href} is a <i>relative</i> link that navigates inside the SPA rather than to the
 * company, so a bare host gains {@code https://}. Anything that is not then http(s) is dropped rather
 * than stored — {@code javascript:} in an href is the interesting case, and a link the grid refuses to
 * render is better than one every future use site must remember to sanitise.
 */
public final class SuppliedText {

    private SuppliedText() {}

    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * A browsable http(s) address, or null. A bare host is promoted rather than refused, because that
     * is what people type and refusing it would lose a field the caller meant to give us.
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
