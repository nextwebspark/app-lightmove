package app.lightmove.api.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import app.lightmove.api.core.config.AuthSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.LockoutSettings;
import app.lightmove.api.core.config.RateLimitSettings;
import app.lightmove.api.core.security.service.PasswordPolicy;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The policy and the encoder must agree on what "too long" means.
 *
 * <p>They did not: the policy counted characters and BCrypt counts UTF-8 bytes, so a password of 41
 * accented characters passed validation and then threw inside {@code encode} — a 500 on signup,
 * password reset and invited signup alike. Anything {@code validate} accepts has to survive
 * {@code hash}, and that is what these assert.
 */
class PasswordPolicyTest {

    private final PasswordPolicy passwords = new PasswordPolicy(properties());

    @Test
    @DisplayName("a password at the 72-byte ceiling is accepted and hashes")
    void seventyTwoBytesIsAccepted() {
        String password = "a".repeat(71) + "1";

        assertThat(passwords.validate(password)).isNull();
        assertThatCode(() -> passwords.hash(password)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a multi-byte password over the ceiling is rejected, not passed to the encoder")
    void multiByteOverTheCeilingIsRejected() {
        // 41 × "é" is 41 characters and 83 bytes — under the old character count, over BCrypt's limit.
        String password = "é".repeat(41) + "1";

        assertThat(password.length()).isLessThan(72);
        assertThat(passwords.validate(password)).isNotNull();
    }

    @Test
    @DisplayName("every password the policy accepts can be hashed")
    void anythingValidatedCanBeHashed() {
        List<String> accepted = List.of(
                "password1",
                "é".repeat(35) + "1",
                "🔐".repeat(16) + "pass1",
                "a".repeat(71) + "1");

        for (String password : accepted) {
            assertThat(passwords.validate(password)).as(password).isNull();
            assertThatCode(() -> passwords.hash(password)).as(password).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("a null stored hash never matches — a federated account is not an open door")
    void nullHashNeverMatches() {
        assertThat(passwords.matches("password1", null)).isFalse();
    }

    /** Strength 4: every assertion here is about length, and cost 12 would spend a second per hash. */
    private static LightMoveProperties properties() {
        AuthSettings auth = new AuthSettings(
                null, null,
                new LockoutSettings(5, Duration.ofMinutes(15)),
                new RateLimitSettings(true, 10, 5, 3, 3, 10, 5, 60),
                // Null extension too, for the same reason as oauth below: nothing here pairs one.
                null,
                Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofHours(24),
                Duration.ofMinutes(30), Duration.ofDays(7),
                // Null oauth: nothing here signs in through a provider, and AuthSettings defaults it.
                true, false, 4, null);
        return new LightMoveProperties(auth, null, null, null, null);
    }
}
