package app.lightmove.api.core.security.token;

/**
 * Which client a refresh-token family was opened for, and therefore how its refresh token is shaped.
 *
 * <p>Not a set of privileges — both clients get the same access token with the same claims. It decides
 * how long the refresh token lives, where it travels, and which route may redeem it.
 */
public enum SessionClient {

    /** The web app. Refresh token in an httpOnly cookie, standard TTL. */
    WEB_APP,

    /** LightMove Capture. Refresh token in a response body, resting in extension storage, shorter TTL. */
    BROWSER_EXTENSION
}
