package app.lightmove.api.core.security.mfa;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 time-based codes as every authenticator app reads them: HMAC-SHA1, six digits, 30-second steps.
 * Written out rather than taken from a library: the algorithm is this page, and the sign-in path is the last
 * place to add a dependency that also ships a QR renderer we do not use.
 */
public final class Totp {

    static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int MODULUS = 1_000_000;

    /** One step either side: a phone a few seconds adrift, or a code typed as the step turned. */
    private static final int WINDOW = 1;

    /** 160 bits, the HMAC-SHA1 block RFC 4226 recommends. */
    private static final int SECRET_BYTES = 20;

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** The secret as an authenticator app's "enter a setup key" box takes it: unpadded Base32. */
    public static String newSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        return base32(secret);
    }

    public static String otpauthUri(String issuer, String accountName, String secret) {
        String label = encode(issuer) + ":" + encode(accountName);
        return "otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d"
                .formatted(label, secret, encode(issuer), DIGITS, STEP_SECONDS);
    }

    public static long stepAt(Instant instant) {
        return Math.floorDiv(instant.getEpochSecond(), STEP_SECONDS);
    }

    /**
     * The step a code belongs to, within the window around {@code now} and after {@code lastUsedStep}, or empty.
     * Every candidate is compared, in constant time, so the answer's timing says nothing about which one matched.
     */
    public static OptionalLong matchingStep(String secret, String code, Instant now, Long lastUsedStep) {
        if (code == null || code.length() != DIGITS || !code.chars().allMatch(Character::isDigit)) {
            return OptionalLong.empty();
        }
        byte[] key = base32Decode(secret);
        byte[] presented = code.getBytes(StandardCharsets.US_ASCII);
        long current = stepAt(now);

        long matched = -1;
        for (long step = current - WINDOW; step <= current + WINDOW; step++) {
            byte[] expected = codeAt(key, step).getBytes(StandardCharsets.US_ASCII);
            boolean fresh = lastUsedStep == null || step > lastUsedStep;
            if (MessageDigest.isEqual(expected, presented) && fresh && matched < 0) {
                matched = step;
            }
        }
        return matched < 0 ? OptionalLong.empty() : OptionalLong.of(matched);
    }

    static String codeAt(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%0" + DIGITS + "d", binary % MODULUS);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 is unavailable", e);
        }
    }

    static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32_ALPHABET.charAt((buffer >> (bits - 5)) & 0x1f));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32_ALPHABET.charAt((buffer << (5 - bits)) & 0x1f));
        }
        return out.toString();
    }

    static byte[] base32Decode(String text) {
        String cleaned = text.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteBuffer out = ByteBuffer.allocate(cleaned.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char character : cleaned.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(character);
            if (value < 0) {
                throw new IllegalArgumentException("Not Base32");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return out.array();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
