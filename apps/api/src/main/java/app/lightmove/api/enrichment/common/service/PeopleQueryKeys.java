package app.lightmove.api.enrichment.common.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.ObjectMapper;

/** A people-cache key: the SHA-256 of a question's canonical spelling, so no key carries a searched term. */
public final class PeopleQueryKeys {

    private PeopleQueryKeys() {
    }

    /** A request body with its keys and every list sorted, so one question in any order is one spelling. */
    public static String canonical(Map<String, Object> body, ObjectMapper json) {
        return json.writeValueAsString(sorted(body, json));
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

    private static Object sorted(Object value, ObjectMapper json) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> ordered = new TreeMap<>();
            map.forEach((key, entry) -> ordered.put(String.valueOf(key), sorted(entry, json)));
            return ordered;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(entry -> sorted(entry, json))
                    .sorted(Comparator.comparing(json::writeValueAsString))
                    .toList();
        }
        return value;
    }
}
