package app.lightmove.api.core.security;

import app.lightmove.api.core.config.AuthSettings;
import app.lightmove.api.core.config.CookieSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.LockoutSettings;
import app.lightmove.api.core.config.RateLimitSettings;
import java.time.Duration;

/**
 * {@link AuthSettings} as {@code application.yml} configures it, for the tests that build one by hand
 * rather than booting a context.
 *
 * <p>It exists because the record is constructed positionally: sixteen arguments, where a new field is
 * one edit per copy and a swapped {@code Duration} pair compiles fine and asserts the wrong thing.
 * One copy makes the next field a single change.
 */
final class TestAuthSettings {

    private TestAuthSettings() {
    }

    static LightMoveProperties production() {
        return withBcryptStrength(12);
    }

    /** Strength 4 for suites that hash repeatedly — cost 12 would spend a second per hash. */
    static LightMoveProperties withBcryptStrength(int bcryptStrength) {
        AuthSettings auth = new AuthSettings(
                null,
                new CookieSettings("lm_refresh", "/api/v1/auth", true, true, "Strict", null),
                new LockoutSettings(5, Duration.ofMinutes(15)),
                new RateLimitSettings(true, 10, 5, 3, 3, 10, 5, 60),
                // Null extension and oauth: nothing built here pairs a device or signs in through a
                // provider, and AuthSettings defaults both.
                null,
                Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofHours(24),
                Duration.ofMinutes(30), Duration.ofDays(7), Duration.ofMinutes(10),
                true, false, bcryptStrength, null);
        return new LightMoveProperties(auth, null, null, null, null, null, null, null, null, null, null, null);
    }
}
