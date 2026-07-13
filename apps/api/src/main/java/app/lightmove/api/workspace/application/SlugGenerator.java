package app.lightmove.api.workspace.application;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Turns a workspace name into the URL-safe slug that appears in {@code lightmove.app/w/{slug}}.
 */
final class SlugGenerator {

    private static final int MAX_LENGTH = 40;
    private static final int MAX_ATTEMPTS = 100;

    private SlugGenerator() {
    }

    /**
     * @param isTaken asked whether a candidate slug already exists; the generator suffixes -2, -3 …
     *                until one is free.
     */
    static String from(String name, Predicate<String> isTaken) {
        String base = slugify(name);
        if (base.isEmpty()) {
            base = "workspace";
        }

        if (!isTaken.test(base)) {
            return base;
        }

        for (int suffix = 2; suffix < MAX_ATTEMPTS; suffix++) {
            String candidate = "%s-%d".formatted(base, suffix);
            if (!isTaken.test(candidate)) {
                return candidate;
            }
        }

        // A hundred firms with the same name is not a real scenario, but silently reusing a taken slug
        // would hand one workspace's URL to another. Fail instead.
        throw new IllegalStateException("Could not find a free slug for: " + name);
    }

    /**
     * Strips accents ("Zürich" → "zurich") before dropping non-alphanumerics, so an accented name
     * yields a readable slug rather than being gutted down to a handful of surviving letters.
     */
    private static String slugify(String name) {
        String normalised = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        String slug = normalised.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        return slug.length() > MAX_LENGTH
                ? slug.substring(0, MAX_LENGTH).replaceAll("-+$", "")
                : slug;
    }
}
