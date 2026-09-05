package app.lightmove.api.customcolumn.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns a spreadsheet header or a typed-in name into the {@code field_key} a column's values are
 * stored under.
 *
 * <p>The key exists so the label can change without orphaning data, which only works if the key is
 * derived once and then left alone. Everything here is therefore about producing a <i>stable</i>,
 * collision-free key from arbitrary text — never about producing a pretty one, which is the label's
 * job.
 */
public final class CustomColumnKeys {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final int MAX_LENGTH = 60;

    private CustomColumnKeys() {
    }

    /**
     * A lower camel-case key for a label — "Ethnicity / Nationality" becomes {@code ethnicityNationality}.
     *
     * <p>Accents are folded rather than stripped so "Región" and "Region" do not become two columns
     * whose headers look identical. A label that reduces to nothing (all punctuation, or a script this
     * fold does not cover) falls back to {@code field}, which the collision suffix below then makes
     * unique — better a mandate with {@code field2} than an import that refuses a file over a header.
     */
    public static String slug(String label) {
        if (label == null || label.isBlank()) {
            return "field";
        }
        String folded = DIACRITICS
                .matcher(Normalizer.normalize(label, Normalizer.Form.NFD))
                .replaceAll("")
                .toLowerCase(Locale.ROOT);
        String[] words = NON_ALPHANUMERIC.split(folded);

        StringBuilder key = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (key.isEmpty()) {
                key.append(word);
            } else {
                key.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
            }
        }
        if (key.isEmpty()) {
            return "field";
        }
        // A key starting with a digit is legal in JSON but reads as a mistake everywhere else.
        if (Character.isDigit(key.charAt(0))) {
            key.insert(0, "field");
        }
        return key.length() > MAX_LENGTH ? key.substring(0, MAX_LENGTH) : key.toString();
    }

    /**
     * The first of {@code key}, {@code key2}, {@code key3}… that the project does not already use.
     *
     * <p>Two different labels can slug to the same key ("Notice period" and "Notice Period!"), and the
     * key is what the values hang off — so a collision has to become a second column rather than
     * quietly writing into the first one's values.
     */
    public static String uniqueWithin(String desiredKey, Set<String> takenKeys) {
        if (!takenKeys.contains(desiredKey)) {
            return desiredKey;
        }
        for (int suffix = 2; ; suffix++) {
            String candidate = desiredKey + suffix;
            if (!takenKeys.contains(candidate)) {
                return candidate;
            }
        }
    }
}
