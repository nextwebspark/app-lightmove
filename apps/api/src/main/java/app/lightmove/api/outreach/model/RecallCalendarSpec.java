package app.lightmove.api.outreach.model;

/**
 * What Recall needs to read one mailbox's calendar itself: the OAuth app the workspace connects through and the
 * mailbox's refresh token. Held only for the call; {@link #toString()} leaves the secret and the token out.
 *
 * @param platform Recall's name for the calendar's host, {@code google_calendar} or {@code microsoft_outlook}
 */
public record RecallCalendarSpec(String platform, String clientId, String clientSecret, String refreshToken,
                                 String email) {

    @Override
    public String toString() {
        return "RecallCalendarSpec[platform=" + platform + ", clientId=" + clientId + ", email=" + email
                + ", clientSecret=<redacted>, refreshToken=<redacted>]";
    }
}
