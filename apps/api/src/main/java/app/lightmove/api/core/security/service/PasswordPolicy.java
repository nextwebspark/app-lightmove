package app.lightmove.api.core.security.service;

import app.lightmove.api.core.config.LightMoveProperties;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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
     *
     * <p>Bytes, not characters, and the distinction is load-bearing: {@code encode} throws
     * {@code IllegalArgumentException} on a 73-byte input, so measuring characters let 41 accented
     * ones (83 bytes in UTF-8) past validation and turned signup into a 500.
     */
    private static final int MAX_BYTES = 72;

    private final PasswordEncoder encoder;

    /**
     * A hash of a value nobody knows, compared against when there is no real hash to compare against.
     *
     * <p>Derived rather than hardcoded so its cost always tracks {@code bcrypt-strength}: a constant
     * baked at one strength would stop matching the real work the moment the setting moved, and the
     * timing it exists to hide would reopen. Costs one BCrypt encode at startup (~250 ms at strength
     * 12) — that is the bean's construction, not a slow application.
     */
    private final String decoyHash;

    public PasswordPolicy(LightMoveProperties properties) {
        this.encoder = new BCryptPasswordEncoder(properties.auth().bcryptStrength());
        this.decoyHash = encoder.encode(UUID.randomUUID().toString());
    }

    /** @return null if acceptable, otherwise the reason it is not — phrased for the user. */
    public String validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "Use at least %d characters".formatted(MIN_LENGTH);
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            // Deliberately not "at most 72 characters": for accented or emoji input that is a lie, and
            // the user is left retyping a password the encoder will refuse again.
            return "Use at most %d characters — fewer if they are accented or emoji".formatted(MAX_BYTES);
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            return "Include at least one number";
        }
        return null;
    }

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /**
     * Whether the password opens this hash. A null hash — a Google-only account, or the caller
     * standing in for an address with no account at all — is always false.
     *
     * <p>It still pays for a comparison first, and that is the whole point. Returning early made a
     * login against an unknown address ~10x faster than one against a real account (26 ms vs 276 ms),
     * which told anyone who asked whether an address is a LightMove customer. Same answer, same cost.
     */
    public boolean matches(String rawPassword, String storedHash) {
        if (storedHash == null) {
            equaliseFailureCost(rawPassword);
            return false;
        }
        return encoder.matches(rawPassword, storedHash);
    }

    /**
     * Spends what a real password check would spend, and learns nothing.
     *
     * <p>For the login paths that refuse before reaching a hash — an unknown address, a locked account,
     * a suspended one. They answer the same {@code INVALID_CREDENTIALS} as a wrong password, and this
     * makes them take the same time saying it. Without it the answer is identical but the clock is not,
     * which is all an attacker needs.
     */
    public void equaliseFailureCost(String rawPassword) {
        encoder.matches(rawPassword, decoyHash);
    }

    /** Exposed for Spring Security's own machinery (e.g. the OAuth2 client). */
    public PasswordEncoder encoder() {
        return encoder;
    }
}
