package app.uncava.api.core.text.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Whether a name typed to confirm a destructive act is the name on screen. Lenient where a keyboard cannot
 * follow — case, spacing, and the en dash a title like "Group CFO – Energy" carries — and strict everywhere else.
 * The SPA's {@code lib/confirmationName.ts} applies the same rule, so its button and this check never disagree.
 */
public final class ConfirmationName {

    private ConfirmationName() {}

    public static boolean matches(String expected, String typed) {
        return expected != null && typed != null && !typed.isBlank() && comparable(expected).equals(comparable(typed));
    }

    private static String comparable(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFKC)
                .replaceAll("[\\u2010-\\u2015\\u2212]", "-")
                .replaceAll("\\s+", " ")
                .strip()
                .toLowerCase(Locale.ROOT);
    }
}
