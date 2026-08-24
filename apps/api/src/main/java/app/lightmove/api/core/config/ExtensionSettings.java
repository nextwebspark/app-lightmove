package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The browser extension's session — {@code lightmove.auth.extension.*}.
 *
 * <p>The extension does not use the refresh <i>cookie</i>: it is {@code SameSite=Strict}, host-only
 * and scoped to {@code /api/v1/auth}, and reaching it from another origin would mean weakening every
 * attribute that protects it. So the extension is paired instead — the signed-in web app mints it a
 * refresh token that travels in a response body and lives in the extension's own storage.
 *
 * <p>That storage is on disk rather than in a cookie jar, which is why the TTL here is its own knob
 * and is much shorter than {@code lightmove.auth.refresh-token-ttl}. Everything else about the token
 * — rotation, reuse detection, revocation — is the ordinary machinery, and re-pairing is a click. The
 * session's label is <i>not</i> configurable and lives on {@code SessionClient}; the device describer
 * matches on it, and a second copy here would drift.
 */
public record ExtensionSettings(

        @DefaultValue(DEFAULT_REFRESH_TOKEN_TTL) Duration refreshTokenTtl
) {

    // A constant rather than a repeated literal: @DefaultValue takes only compile-time constants, and
    // AuthSettings needs the same value when the whole branch is absent from yml.
    static final String DEFAULT_REFRESH_TOKEN_TTL = "14d";

    /** What binding produces when {@code lightmove.auth.extension} is not in the configuration at all. */
    static ExtensionSettings defaults() {
        return new ExtensionSettings(Duration.parse("P" + DEFAULT_REFRESH_TOKEN_TTL.toUpperCase()));
    }
}
