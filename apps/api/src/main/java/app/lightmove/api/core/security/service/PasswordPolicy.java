package app.lightmove.api.core.security.service;

import app.lightmove.api.core.config.LightMoveProperties;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Hashes and checks passwords; the strength rule is the mockup's "at least 8 characters, with one number". */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    /**
     * BCrypt's limit. Bytes, not characters: {@code encode} throws on a 73-byte input, so measuring
     * characters let 41 accented ones (83 bytes) past validation and turned signup into a 500.
     */
    private static final int MAX_BYTES = 72;

    private final PasswordEncoder encoder;

    /** Derived at startup, not hardcoded, so its cost tracks {@code bcrypt-strength} and the timing stays hidden. */
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
            // Not "at most 72 characters": for accented or emoji input that is a lie.
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
     * A null hash is always false, but still pays for a comparison: returning early made an unknown
     * address ~10x faster (26 ms vs 276 ms), revealing which addresses are customers.
     */
    public boolean matches(String rawPassword, String storedHash) {
        if (storedHash == null) {
            equaliseFailureCost(rawPassword);
            return false;
        }
        return encoder.matches(rawPassword, storedHash);
    }

    /** Spends a real check's time for a refusal that never reaches a hash, so the clock matches the answer. */
    public void equaliseFailureCost(String rawPassword) {
        encoder.matches(rawPassword, decoyHash);
    }
}
