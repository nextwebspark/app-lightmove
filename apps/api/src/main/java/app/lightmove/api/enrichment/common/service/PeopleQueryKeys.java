package app.lightmove.api.enrichment.common.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** A people-cache key: the SHA-256 of a question's canonical spelling, so no key carries a searched term. */
public final class PeopleQueryKeys {

    private PeopleQueryKeys() {
    }

    public static String of(String canonicalQuestion) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalQuestion.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
