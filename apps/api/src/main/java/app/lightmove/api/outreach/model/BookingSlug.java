package app.lightmove.api.outreach.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** A consultant's name as the path of their booking link: {@code Yara Haddad} reads {@code yara-haddad}. */
public final class BookingSlug {

    /** What a link's path may be; anything else is answered as no page at all. */
    public static final Pattern SHAPE = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?");

    private static final int MAX_BASE = 48;
    private static final int MAX_ATTEMPTS = 100;

    private BookingSlug() {
    }

    /** @param isTaken asked per candidate; -2, -3 … are tried until one is free */
    public static String from(String fullName, Predicate<String> isTaken) {
        String base = slugify(fullName);
        if (base.isEmpty()) {
            base = "consultant";
        }
        if (!isTaken.test(base)) {
            return base;
        }
        for (int suffix = 2; suffix < MAX_ATTEMPTS; suffix++) {
            String candidate = base + "-" + suffix;
            if (!isTaken.test(candidate)) {
                return candidate;
            }
        }
        // Reusing a taken slug would send one consultant's executives to another's calendar.
        throw new IllegalStateException("No free booking slug for " + base);
    }

    private static String slugify(String name) {
        String plain = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.length() > MAX_BASE ? slug.substring(0, MAX_BASE).replaceAll("-+$", "") : slug;
    }
}
