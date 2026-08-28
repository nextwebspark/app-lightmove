package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationStyle;

/**
 * The browser extension's session — {@code lightmove.auth.extension.*}.
 *
 * <p>Shorter than the web app's TTL because the extension's refresh token rests in its own storage
 * rather than in an httpOnly cookie. Why it is paired at all: {@code .claude/skills/chrome-extension}.
 */
public record ExtensionSettings(

        @DefaultValue(DEFAULT_REFRESH_TOKEN_TTL) Duration refreshTokenTtl
) {

    // A constant rather than a repeated literal: @DefaultValue takes only compile-time constants, and
    // AuthSettings needs the same value when the whole branch is absent from yml.
    static final String DEFAULT_REFRESH_TOKEN_TTL = "14d";

    /** Boot's own parser for the yml value, so the two default paths agree by construction. */
    static ExtensionSettings defaults() {
        return new ExtensionSettings(DurationStyle.SIMPLE.parse(DEFAULT_REFRESH_TOKEN_TTL));
    }
}
