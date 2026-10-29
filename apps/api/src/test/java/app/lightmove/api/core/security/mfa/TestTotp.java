package app.lightmove.api.core.security.mfa;

import java.time.Instant;

/** What an authenticator app shows for a setup key, for suites outside this package. */
public final class TestTotp {

    private TestTotp() {
    }

    public static String codeAt(String secret, Instant instant) {
        return Totp.codeAt(Totp.base32Decode(secret), Totp.stepAt(instant));
    }

    public static String codeAtStep(String secret, long step) {
        return Totp.codeAt(Totp.base32Decode(secret), step);
    }

    public static long stepAt(Instant instant) {
        return Totp.stepAt(instant);
    }
}
