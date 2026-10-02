package app.lightmove.api.outreach.model;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The path of a consultant's booking link: their name for the reader, and eight random characters so it
 * cannot be guessed. The page books without a session, so whoever holds the link can book as anyone; the
 * link has to be something only the emailed executive was given.
 */
public final class BookingSlug {

    /** What a link's path may be; anything else is answered as no page at all. */
    public static final Pattern SHAPE = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?");

    private static final int MAX_BASE = 40;
    private static final int SECRET_LENGTH = 8;
    private static final int MAX_ATTEMPTS = 5;
    private static final String SECRET_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private BookingSlug() {
    }

    /** @param isTaken asked per candidate; another secret is drawn while it answers true */
    public static String from(String fullName, Predicate<String> isTaken) {
        String base = slugify(fullName);
        String prefix = base.isEmpty() ? "consultant" : base;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = prefix + "-" + secret();
            if (!isTaken.test(candidate)) {
                return candidate;
            }
        }
        // Reusing a taken slug would send one consultant's executives to another's calendar.
        throw new IllegalStateException("No free booking slug for " + prefix);
    }

    private static String secret() {
        StringBuilder secret = new StringBuilder(SECRET_LENGTH);
        for (int index = 0; index < SECRET_LENGTH; index++) {
            secret.append(SECRET_ALPHABET.charAt(RANDOM.nextInt(SECRET_ALPHABET.length())));
        }
        return secret.toString();
    }

    private static String slugify(String name) {
        String plain = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.length() > MAX_BASE ? slug.substring(0, MAX_BASE).replaceAll("-+$", "") : slug;
    }
}
