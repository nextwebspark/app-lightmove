package app.lightmove.api.auth.infrastructure;

import app.lightmove.api.common.config.LightMoveProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Builds the cookie that carries the refresh token.
 *
 * <p>Every attribute here is a defence, not a formality:
 *
 * <ul>
 *   <li><b>httpOnly</b> — script cannot read it. This is the whole reason the refresh token lives in
 *       a cookie instead of {@code localStorage}: one compromised npm dependency in the SPA can read
 *       {@code localStorage}, and would walk away with a 30-day credential to a product holding
 *       executive-candidate PII.
 *   <li><b>Secure</b> — never sent over plain HTTP. False only in local dev, where there is no TLS.
 *   <li><b>SameSite=Strict</b> — the browser will not attach it to a request originating from another
 *       site, which is the primary CSRF defence for {@code /auth/refresh}.
 *   <li><b>Path=/api/v1/auth</b> — the cookie is attached only to the auth endpoints, not to every
 *       API call. A token that is not on the wire cannot be captured from it.
 * </ul>
 */
@Component
public class RefreshCookieFactory {

    private final LightMoveProperties.Auth.Cookie config;
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
