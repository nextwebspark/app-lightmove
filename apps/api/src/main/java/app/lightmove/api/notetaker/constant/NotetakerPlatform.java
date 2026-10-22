package app.lightmove.api.notetaker.constant;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/**
 * The video service a call runs on. {@link #of} names it for the drawer, the calendar's own word included;
 * {@link #hostingAt} is what a bot is sent to, an https link on the service's own host and nothing else.
 */
public enum NotetakerPlatform {

    GOOGLE_MEET,
    MICROSOFT_TEAMS,
    ZOOM;

    public static Optional<NotetakerPlatform> of(String joinUrl, String conferencingProvider) {
        Optional<NotetakerPlatform> hosted = ofHost(hostOf(joinUrl));
        if (hosted.isPresent()) {
            return hosted;
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

    /**
     * A bot opens this link in a browser inside our network, so a calendar invite's word for it is never enough:
     * an event titled "Google Meet" whose link points anywhere else is not joined.
     */
    public static Optional<NotetakerPlatform> hostingAt(String joinUrl) {
        URI link = linkOf(joinUrl);
        if (link == null || !"https".equalsIgnoreCase(link.getScheme()) || link.getUserInfo() != null
                || link.getPort() != -1) {
            return Optional.empty();
        }
        return ofHost(hostOf(joinUrl));
    }

    private static Optional<NotetakerPlatform> ofHost(String host) {
        if (host.equals("meet.google.com")) {
            return Optional.of(GOOGLE_MEET);
        }
        if (host.equals("teams.microsoft.com") || host.equals("teams.live.com")) {
            return Optional.of(MICROSOFT_TEAMS);
        }
        if (host.equals("zoom.us") || host.endsWith(".zoom.us")) {
            return Optional.of(ZOOM);
        }
        return Optional.empty();
    }

    private static String hostOf(String joinUrl) {
        URI link = linkOf(joinUrl);
        String host = link == null ? null : link.getHost();
        return host == null ? "" : host.toLowerCase(Locale.ROOT);
    }

    private static URI linkOf(String joinUrl) {
        try {
            return URI.create(joinUrl.trim());
        } catch (IllegalArgumentException | NullPointerException unreadable) {
            return null;
        }
    }
}
