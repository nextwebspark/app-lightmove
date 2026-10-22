package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.ZoomMeeting;
import java.net.URI;
import java.time.Instant;

/**
 * Zoom's OAuth consent screen and the slice of its REST API a booked call needs. Scopes are set on the Zoom app
 * itself, not asked on the consent screen. Never retried: a meeting made twice is two links on one calendar.
 */
public interface ZoomApi {

    URI authorizationUri(ProviderCredentials app, String state, URI redirectUri);

    /** Zoom's id for the account {@code accessToken} was issued for. */
    String userIdOf(String accessToken);

    ZoomMeeting createMeeting(String accessToken, String topic, Instant startsAt, int minutes);

    /** Moves a meeting; its join link stays the same, so an invite already sent still opens it. */
    void moveMeeting(String accessToken, String meetingId, Instant startsAt, int minutes);

    void deleteMeeting(String accessToken, String meetingId);

    /**
     * A short-lived on-behalf-of token for one meeting ({@code user:read:token}): what lets a bot into a Zoom call under
     * the consultant's own account, while they are in it.
     */
    String onBehalfToken(String accessToken, String meetingId);

    /** Withdraws the refresh token at Zoom, which authenticates the app by HTTP Basic to do it. */
    void revoke(ProviderCredentials app, String refreshToken);
}
