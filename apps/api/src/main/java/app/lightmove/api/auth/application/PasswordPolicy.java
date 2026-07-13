package app.lightmove.api.auth.application;

import app.lightmove.api.common.config.LightMoveProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Hashes and checks passwords, and knows what makes one acceptable.
 *
 * <p>The strength rule is the mockup's, verbatim: <i>"at least 8 characters, with one number"</i>.
 * Deliberately not a maze of character-class requirements — those push people towards
 * {@code Password1!}, which is both compliant and among the first thousand guesses any attacker
 * makes. Length and a real lockout do more than complexity theatre.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    /**
     * BCrypt's own limit: it silently ignores everything past 72 bytes. Capping explicitly means a
     * long passphrase is rejected outright rather than quietly truncated — which would let two
     * different passwords that share a 72-byte prefix both open the same account.
     */
    private static final int MAX_LENGTH = 72;

    private final PasswordEncoder encoder;

    public PasswordPolicy(LightMoveProperties properties) {
        this.encoder = new BCryptPasswordEncoder(properties.auth().bcryptStrength());
    }

    /** @return null if acceptable, otherwise the reason it is not — phrased for the user. */
    public String validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "Use at least %d characters".formatted(MIN_LENGTH);
        }
        if (password.length() > MAX_LENGTH) {
            return "Use at most %d characters".formatted(MAX_LENGTH);
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            return "Include at least one number";
        }
        return null;
    }

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedHash) {
        if (storedHash == null) {
            // A Google-only account has no password. Returning false rather than throwing keeps the
            // caller on the one code path that treats every failure identically — see the note on
            // AuthService.login about not leaking which accounts are federated.
            return false;
        }
        return encoder.matches(rawPassword, storedHash);
    }

    /** Exposed for Spring Security's own machinery (e.g. the OAuth2 client). */
    public PasswordEncoder encoder() {
        return encoder;
    }
}
