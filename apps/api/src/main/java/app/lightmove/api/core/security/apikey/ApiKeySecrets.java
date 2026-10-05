package app.lightmove.api.core.security.apikey;

import app.lightmove.api.core.security.token.Tokens;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.zip.CRC32;

/**
 * A kind's prefix, 256 random bits in base62, then a base62 CRC32 of the rest: the checksum refuses a typo
 * without a database read and lets secret scanners tell a real key from a lookalike.
 */
public final class ApiKeySecrets {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BODY_LENGTH = 43;
    private static final int CHECKSUM_LENGTH = 6;
    private static final int HINT_LENGTH = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeySecrets() {
    }

    public static MintedApiKey mint(ApiKeyKind kind) {
        StringBuilder body = new StringBuilder(BODY_LENGTH);
        for (int i = 0; i < BODY_LENGTH; i++) {
            body.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        String unchecked = kind.secretPrefix() + body;
        String secret = unchecked + checksumOf(unchecked);
        return new MintedApiKey(secret, Tokens.hash(secret), hintOf(kind, secret));
    }

    /** The prefix and the checksum's tail: enough to recognise a key, none of its random part. */
    private static String hintOf(ApiKeyKind kind, String secret) {
        return kind.secretPrefix() + "…" + secret.substring(secret.length() - HINT_LENGTH);
    }

    /** A known prefix, the right length, base62 throughout and a checksum that agrees. */
    public static boolean isWellFormed(String presented) {
        if (presented == null) {
            return false;
        }
        for (ApiKeyKind kind : ApiKeyKind.values()) {
            String prefix = kind.secretPrefix();
            if (presented.startsWith(prefix)
                    && presented.length() == prefix.length() + BODY_LENGTH + CHECKSUM_LENGTH
                    && isBase62(presented.substring(prefix.length()))) {
                int split = presented.length() - CHECKSUM_LENGTH;
                return checksumOf(presented.substring(0, split)).equals(presented.substring(split));
            }
        }
        return false;
    }

    private static boolean isBase62(String value) {
        return value.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0);
    }

    private static String checksumOf(String value) {
        CRC32 crc = new CRC32();
        crc.update(value.getBytes(StandardCharsets.US_ASCII));
        long remaining = crc.getValue();
        char[] digits = new char[CHECKSUM_LENGTH];
        for (int i = CHECKSUM_LENGTH - 1; i >= 0; i--) {
            digits[i] = ALPHABET.charAt((int) (remaining % ALPHABET.length()));
            remaining /= ALPHABET.length();
        }
        return new String(digits);
    }
}
