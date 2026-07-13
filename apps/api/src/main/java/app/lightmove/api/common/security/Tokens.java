package app.lightmove.api.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Mints and hashes the opaque tokens — refresh, email verification, invitation.
 *
 * <p>Two rules, applied everywhere:
 *
 * <ul>
 *   <li><b>Generated with {@link SecureRandom}.</b> Never {@code Math.random()} or {@code Random},
 *       whose output is predictable from a handful of observations — an attacker who sees a few
 *       invitation links could compute the next one.
 *   <li><b>Stored only as a SHA-256 hash.</b> The plaintext leaves in an email or a cookie and is
 *       never written down. If the database leaks, the tokens in it cannot be redeemed.
 * </ul>
 *
 * <p>SHA-256 rather than BCrypt here, unlike passwords, and that is not an oversight. BCrypt is slow
 * <i>on purpose</i>, to make guessing a low-entropy human password expensive. These tokens carry 256
 * bits of entropy from a CSPRNG — there is nothing to guess, so the slowness would buy no security and
 * would cost a deliberate delay on every single API call that refreshes a token.
 */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32; // 256 bits

    private Tokens() {
    }

    /** A URL-safe token, unpadded so it survives being pasted into an email client. */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 64 hex characters — matches the {@code varchar(64)} the token_hash columns declare. */
    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the JDK spec. Unreachable on any conformant JVM.
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
