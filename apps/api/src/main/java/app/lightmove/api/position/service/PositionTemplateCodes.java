package app.lightmove.api.position.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A template's code: a slug of its title, fixed once written, because every firm's copy of a library
 * template and every import file refers to the template by it.
 */
final class PositionTemplateCodes {

    private static final Pattern FORMAT = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final int MAX_LENGTH = 64;
    private static final int MAX_SLUG_LENGTH = 56;

    // A code is a path segment beside literal routes — the API's, and the SPA's /settings/templates/new —
    // so it may not spell one of them.
    private static final Set<String> RESERVED = Set.of("export", "import", "schema", "new");

    private PositionTemplateCodes() {
    }

    static boolean isValid(String code) {
        return code != null && code.length() <= MAX_LENGTH && FORMAT.matcher(code).matches()
                && !RESERVED.contains(code);
    }

    static String slugOf(String title) {
        String ascii = Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_SLUG_LENGTH) {
            slug = slug.substring(0, MAX_SLUG_LENGTH).replaceAll("-+$", "");
        }
        if (slug.isEmpty()) {
            return "template";
        }
        return RESERVED.contains(slug) ? slug + "-template" : slug;
    }

    static String unusedCode(String title, Set<String> taken) {
        String base = slugOf(title);
        String candidate = base;
        for (int suffix = 2; taken.contains(candidate); suffix++) {
            candidate = base + "-" + suffix;
        }
        return candidate;
    }
}
