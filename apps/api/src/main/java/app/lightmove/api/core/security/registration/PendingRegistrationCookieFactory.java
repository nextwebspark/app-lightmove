package app.lightmove.api.core.security.registration;

import app.lightmove.api.core.config.CookieSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.security.model.PendingRegistrationIssued;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The held registration's cookie, written as the two-factor challenge's is: httpOnly, Secure and SameSite as the
 * refresh cookie, and scoped to the one route that reads it, so the sign-in popup can leave it for the opener.
 */
@Component
public class PendingRegistrationCookieFactory {

    public static final String NAME = "lm_pending_registration";
    static final String PATH = "/api/v1/auth/oauth/registration";

    private final CookieSettings cookie;

    public PendingRegistrationCookieFactory(LightMoveProperties properties) {
        this.cookie = properties.auth().cookie();
    }

    public ResponseCookie create(PendingRegistrationIssued pending) {
        return base(pending.registrationToken()).maxAge(pending.expiresIn()).build();
    }

    public ResponseCookie expire() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(NAME, value)
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
