package app.lightmove.api.core.security.mfa;

import app.lightmove.api.core.config.CookieSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.MfaSettings;
import app.lightmove.api.core.security.model.MfaChallengeIssued;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The sign-in challenge's cookie, written as the refresh cookie is: httpOnly, Secure and SameSite as configured, and
 * scoped to {@code /api/v1/auth/mfa} so it is on the wire only when a code is sent. A cookie rather than a body, so a
 * provider's sign-in popup can leave it where the opener's next request finds it without either window passing it on.
 */
@Component
public class MfaChallengeCookieFactory {

    static final String PATH = "/api/v1/auth/mfa";

    private final CookieSettings cookie;
    private final MfaSettings config;

    public MfaChallengeCookieFactory(LightMoveProperties properties) {
        this.cookie = properties.auth().cookie();
        this.config = properties.auth().mfa();
    }

    public ResponseCookie create(MfaChallengeIssued challenge) {
        return base(challenge.challengeToken()).maxAge(challenge.expiresIn()).build();
    }

    public ResponseCookie expire() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(config.challengeCookieName(), value)
                .httpOnly(true)
                .secure(cookie.secure())
                .sameSite(cookie.sameSite())
                .path(PATH);
        if (StringUtils.hasText(cookie.domain())) {
            builder.domain(cookie.domain());
        }
        return builder;
    }
}
