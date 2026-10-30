package app.lightmove.api.core.security.mfa;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** RFC 6238's own vectors, cut to the six digits every authenticator app shows, and the window around them. */
class TotpTest {

    /** RFC 6238 Appendix B's SHA-1 seed, "12345678901234567890". */
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @ParameterizedTest(name = "at {0}s the code is {1}")
    @CsvSource({
            "59, 287082",
            "1111111109, 081804",
            "1111111111, 050471",
            "1234567890, 005924",
            "2000000000, 279037",
            "20000000000, 353130"})
    @DisplayName("each RFC 6238 test vector, as six digits")
    void matchesTheRfcVectors(long epochSeconds, String code) {
        assertThat(Totp.codeAt(RFC_KEY, Totp.stepAt(Instant.ofEpochSecond(epochSeconds)))).isEqualTo(code);
    }

    @Test
    @DisplayName("Base32 reads and writes the setup key the apps take")
    void base32RoundTrips() {
        assertThat(Totp.base32(RFC_KEY)).isEqualTo(RFC_SECRET);
        assertThat(Totp.base32Decode(RFC_SECRET)).isEqualTo(RFC_KEY);
        assertThat(Totp.base32Decode(Totp.newSecret())).hasSize(20);
    }

    @Test
    @DisplayName("a code one step either side of now passes; two steps away does not")
    void acceptsOneStepEitherSide() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        long step = Totp.stepAt(now);

        assertThat(Totp.matchingStep(RFC_SECRET, code(step - 1), now, null)).hasValue(step - 1);
        assertThat(Totp.matchingStep(RFC_SECRET, code(step), now, null)).hasValue(step);
        assertThat(Totp.matchingStep(RFC_SECRET, code(step + 1), now, null)).hasValue(step + 1);
        assertThat(Totp.matchingStep(RFC_SECRET, code(step - 2), now, null)).isEmpty();
        assertThat(Totp.matchingStep(RFC_SECRET, code(step + 2), now, null)).isEmpty();
    }

    @Test
    @DisplayName("a code for a step already used, or an earlier one, is refused")
    void refusesAReplayedStep() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        long step = Totp.stepAt(now);

        assertThat(Totp.matchingStep(RFC_SECRET, code(step), now, step)).isEmpty();
        assertThat(Totp.matchingStep(RFC_SECRET, code(step - 1), now, step)).isEmpty();
        assertThat(Totp.matchingStep(RFC_SECRET, code(step + 1), now, step)).hasValue(step + 1);
    }

    @Test
    @DisplayName("anything but six digits is not a code")
    void refusesMalformedCodes() {
        Instant now = Instant.ofEpochSecond(59);
        assertThat(Totp.matchingStep(RFC_SECRET, null, now, null)).isEmpty();
        assertThat(Totp.matchingStep(RFC_SECRET, "28708", now, null)).isEmpty();
        assertThat(Totp.matchingStep(RFC_SECRET, "2870821", now, null)).isEmpty();
        assertThat(Totp.matchingStep(RFC_SECRET, "28708a", now, null)).isEmpty();
    }

    @Test
    @DisplayName("the otpauth link names Uncava and the account, and carries the secret")
    void buildsTheOtpauthLink() {
        assertThat(Totp.otpauthUri("Uncava", "sara+x@firm.example", RFC_SECRET))
                .isEqualTo("otpauth://totp/Uncava:sara%2Bx%40firm.example?secret=" + RFC_SECRET
                        + "&issuer=Uncava&algorithm=SHA1&digits=6&period=30");
    }

    private static String code(long step) {
        return Totp.codeAt(RFC_KEY, step);
    }
}
