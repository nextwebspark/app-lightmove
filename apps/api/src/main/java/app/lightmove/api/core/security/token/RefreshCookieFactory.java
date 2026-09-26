package app.lightmove.api.core.security.token;

import app.lightmove.api.core.config.CookieSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The refresh-token cookie; every attribute is a defence. <b>httpOnly</b>, so a compromised npm
 * dependency cannot read a 30-day credential; <b>Secure</b> outside local dev; <b>SameSite=Strict</b>,
 * the CSRF defence for {@code /auth/refresh}; <b>Path=/api/v1/auth</b>, so it is off the wire elsewhere.
 */
@Component
public class RefreshCookieFactory {

    private final CookieSettings config;
    private final Duration ttl;

    public RefreshCookieFactory(LightMoveProperties properties) {
        this.config = properties.auth().cookie();
        this.ttl = properties.auth().refreshTokenTtl();
    }

    public String name() {
        return config.name();
    }

    public ResponseCookie create(String refreshToken) {
        return base(refreshToken).maxAge(ttl).build();
    }

    /**
     * An expired, empty cookie — how a cookie is deleted. Must carry the same name, path and domain as
     * the original, or the browser treats it as a different cookie and leaves the real one in place.
     */
    public ResponseCookie expire() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(config.name(), value)
                .httpOnly(config.httpOnly())
                .secure(config.secure())
                .sameSite(config.sameSite())
                .path(config.path());

        if (StringUtils.hasText(config.domain())) {
            builder.domain(config.domain());
        }
        return builder;
    }
}
