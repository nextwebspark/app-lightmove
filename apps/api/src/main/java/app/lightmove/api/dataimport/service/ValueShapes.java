package app.lightmove.api.dataimport.service;

import app.lightmove.api.dataimport.model.SheetColumn.ValueShape;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Judges what a column's values look like, from the values themselves.
 *
 * <p>A header alone is often ambiguous — "Contact" could be an email, a phone number or a person's
 * name — and a column that is 90% email addresses settles it. The mapping step is given this shape
 * rather than the values themselves, which is what keeps a longlist's PII out of everything above it.
 *
 * <p>A supermajority rather than every row, because real files carry a blank, a "n/a" and a typo in
 * any column of a thousand rows, and a rule that needed all of them to agree would answer
 * {@code SHORT_TEXT} for everything.
 */
final class ValueShapes {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern URL = Pattern.compile("^(https?://|www\\.)\\S+$|^[\\w.-]+\\.(com|net|org|ae|sa|qa|kw|om|bh|io|co)(/\\S*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("^[-+]?[\\d,\\s]*\\.?\\d+%?$");
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}([T ].*)?$|^\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}$");
    private static final Set<String> BOOLEANS =
            Set.of("true", "false", "yes", "no", "y", "n", "1", "0");

    /** What share of a column's non-blank values must agree before the column is called that shape. */
    private static final double SUPERMAJORITY = 0.7;

    /** Beyond this a value is prose, not a field — the difference between a note and a job title. */
    private static final int LONG_TEXT_LENGTH = 120;

    private ValueShapes() {
    }

    static ValueShape of(List<String> values) {
        if (values.isEmpty()) {
            return ValueShape.BLANK;
        }
        // Ordered most specific first: an email is also short text, and a year is also a number, so
        // the first rule that a supermajority satisfies is the most informative one that is true.
        if (shareMatching(values, ValueShapes::isEmail) >= SUPERMAJORITY) {
            return ValueShape.EMAIL;
        }
        if (shareMatching(values, ValueShapes::isUrl) >= SUPERMAJORITY) {
            return ValueShape.URL;
        }
        if (shareMatching(values, ValueShapes::isBoolean) >= SUPERMAJORITY) {
            return ValueShape.BOOLEAN;
        }
        if (shareMatching(values, ValueShapes::isDate) >= SUPERMAJORITY) {
            return ValueShape.DATE;
        }
        if (shareMatching(values, ValueShapes::isNumber) >= SUPERMAJORITY) {
            return ValueShape.NUMBER;
        }
        double longShare = shareMatching(values, value -> value.length() > LONG_TEXT_LENGTH);
        return longShare >= 0.3 ? ValueShape.LONG_TEXT : ValueShape.SHORT_TEXT;
    }

    private static double shareMatching(List<String> values, java.util.function.Predicate<String> rule) {
        long matching = values.stream().filter(rule).count();
        return (double) matching / values.size();
    }

    private static boolean isEmail(String value) {
        return EMAIL.matcher(value).matches();
    }

    private static boolean isUrl(String value) {
        return URL.matcher(value).matches();
    }

    private static boolean isBoolean(String value) {
        return BOOLEANS.contains(value.toLowerCase(Locale.ROOT));
    }

    private static boolean isDate(String value) {
        return DATE.matcher(value).matches();
    }

    private static boolean isNumber(String value) {
        return NUMBER.matcher(value).matches() && value.chars().anyMatch(Character::isDigit);
    }
}
