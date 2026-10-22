package app.lightmove.api.core.webhook.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * A Svix-signed webhook delivery, as Recall.ai sends them: an HMAC-SHA256 of {@code id.timestamp.body} under the base64
 * key after {@code whsec_}, sent as {@code v1,<base64>} (several, space-separated, while a key rotates).
 */
public final class SvixSignatures {

    /** A delivery older or newer than this is refused, so a captured one cannot be replayed later. */
    public static final Duration TOLERANCE = Duration.ofMinutes(5);

    private static final String SECRET_PREFIX = "whsec_";

    private SvixSignatures() {
    }

    /** A blank secret verifies nothing: an unsigned public endpoint would let anyone speak for the vendor. */
    public static boolean verifies(String secret, String id, String timestamp, String signature, byte[] body,
                                   Instant now) {
        if (secret == null || secret.isBlank() || id == null || timestamp == null || signature == null
                || body == null) {
            return false;
        }
        long sentAt;
        try {
            sentAt = Long.parseLong(timestamp.strip());
        } catch (NumberFormatException notATimestamp) {
            return false;
        }
        if (Duration.between(Instant.ofEpochSecond(sentAt), now).abs().compareTo(TOLERANCE) > 0) {
            return false;
        }
        byte[] expected;
        try {
            String key = secret.startsWith(SECRET_PREFIX) ? secret.substring(SECRET_PREFIX.length()) : secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(key), "HmacSHA256"));
            mac.update((id + "." + timestamp.strip() + ".").getBytes(StandardCharsets.UTF_8));
            expected = mac.doFinal(body);
        } catch (GeneralSecurityException | IllegalArgumentException unusable) {
            return false;
        }
        for (String candidate : signature.split(" ")) {
            int comma = candidate.indexOf(',');
            if (comma < 0 || !"v1".equals(candidate.substring(0, comma))) {
                continue;
            }
            byte[] presented;
            try {
                presented = Base64.getDecoder().decode(candidate.substring(comma + 1));
            } catch (IllegalArgumentException notBase64) {
                continue;
            }
            if (MessageDigest.isEqual(expected, presented)) {
                return true;
            }
        }
        return false;
    }
}
