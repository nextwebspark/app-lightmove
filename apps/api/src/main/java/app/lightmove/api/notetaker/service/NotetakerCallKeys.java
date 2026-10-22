package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.security.token.Tokens;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;

/**
 * One call, however many calendars hold it: its join link without query or fragment (a calendar may add
 * tracking to one copy) and its start. Hashed, so a long Teams link keys a short column.
 */
final class NotetakerCallKeys {

    private NotetakerCallKeys() {
    }

    static String of(String joinUrl, Instant startsAt) {
        return Tokens.hash(normalised(joinUrl) + "|" + startsAt.getEpochSecond());
    }

    private static String normalised(String joinUrl) {
        String trimmed = joinUrl.trim();
        try {
            URI uri = URI.create(trimmed);
            if (uri.getHost() == null) {
                return trimmed;
            }
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            while (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return uri.getHost().toLowerCase(Locale.ROOT) + path;
        } catch (IllegalArgumentException unreadable) {
            return trimmed;
        }
    }
}
