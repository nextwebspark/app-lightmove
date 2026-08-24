package app.lightmove.api.core.security.token;

/**
 * Which client a session belongs to, and therefore how its refresh token is shaped.
 *
 * <p>Not a security boundary — the two clients get the same access token with the same claims, and
 * every authorisation decision is made the same way for both. It decides two things only: how long
 * the refresh token lives, and what Settings → Active sessions calls the entry.
 */
public enum SessionClient {

    /** The web app. Refresh token in the cookie, standard TTL, session labelled from its User-Agent. */
    WEB_APP(null),

    /**
     * LightMove Capture, the browser extension. Its refresh token travels in a response body and rests
     * in extension storage rather than a cookie jar, so it gets a shorter TTL of its own.
     */
    BROWSER_EXTENSION("LightMove Capture (browser extension)");

    private final String sessionLabel;

    SessionClient(String sessionLabel) {
        this.sessionLabel = sessionLabel;
    }

    /**
     * What to record as the session's User-Agent, or null to record the caller's own.
     *
     * <p>A fixed string for the extension, and it has to be: its fetches carry the host browser's
     * User-Agent, indistinguishable from the browser around it, so the session list would show two
     * identical "Chrome — macOS" entries and a consultant could not tell which one to revoke. This is
     * also what {@code DeviceDescriber} matches on, which is why the label is a constant here rather
     * than a configuration knob — two copies of it would drift and the icon would silently go wrong.
     */
    public String sessionLabel() {
        return sessionLabel;
    }
}
