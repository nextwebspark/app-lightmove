package app.lightmove.api.notetaker.constant;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/** The video service a call runs on, read off its join link first and the calendar's name for it second. */
public enum NotetakerPlatform {

    GOOGLE_MEET,
    MICROSOFT_TEAMS,
    ZOOM;

    public static Optional<NotetakerPlatform> of(String joinUrl, String conferencingProvider) {
        String host = hostOf(joinUrl);
        if (host.equals("meet.google.com")) {
            return Optional.of(GOOGLE_MEET);
        }
        if (host.equals("teams.microsoft.com") || host.equals("teams.live.com")) {
            return Optional.of(MICROSOFT_TEAMS);
        }
        if (host.equals("zoom.us") || host.endsWith(".zoom.us")) {
            return Optional.of(ZOOM);
        }
        String named = conferencingProvider == null ? "" : conferencingProvider.toLowerCase(Locale.ROOT);
        if (named.contains("meet")) {
            return Optional.of(GOOGLE_MEET);
        }
        if (named.contains("teams")) {
            return Optional.of(MICROSOFT_TEAMS);
        }
        if (named.contains("zoom")) {
            return Optional.of(ZOOM);
        }
        return Optional.empty();
    }

    private static String hostOf(String joinUrl) {
        try {
            String host = URI.create(joinUrl.trim()).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException | NullPointerException unreadable) {
            return "";
        }
    }
}
