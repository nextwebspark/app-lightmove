package app.lightmove.api.dataimport.service;

import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.common.constant.NoticePeriod;
import app.lightmove.api.common.constant.NoticeUnit;
import app.lightmove.api.common.constant.Seniority;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a spreadsheet cell into the value a typed field wants, reading "1,200" or "AED 450,000" where
 * unambiguous. An unreadable value is {@code null}, never a thrown row.
 */
final class RowValues {

    private RowValues() {
    }

    static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Trims to a column's ceiling so an over-long cell shortens rather than failing Bean Validation. */
    static String text(String value, int maxLength) {
        String trimmed = text(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    static Integer integer(String value) {
        Long parsed = number(value);
        if (parsed == null || parsed > Integer.MAX_VALUE || parsed < Integer.MIN_VALUE) {
            return null;
        }
        return parsed.intValue();
    }

    /** A fraction is rounded: every field this feeds is a count or a whole-currency figure. */
    static Long number(String value) {
        String trimmed = text(value);
        if (trimmed == null) {
            return null;
        }
        String digits = trimmed.replaceAll("[^0-9.\\-]", "");
        if (digits.isEmpty() || digits.equals("-") || digits.equals(".")) {
            return null;
        }
        try {
            return Math.round(Double.parseDouble(digits));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A three-letter currency code, or null; "AED 450,000" still means AED. */
    static String currency(String value) {
        String trimmed = text(value);
        if (trimmed == null) {
            return null;
        }
        String letters = trimmed.replaceAll("[^A-Za-z]", "");
        return letters.length() == 3 ? letters.toUpperCase(Locale.ROOT) : null;
    }

    /** Matches the wire token, the enum name and the spellings files carry ("N minus 1", "CSuite"). */
    static String seniority(String value) {
        String trimmed = text(value);
        if (trimmed == null) {
            return null;
        }
        String key = trimmed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        String canonical = SENIORITY_SPELLINGS.get(key);
        if (canonical != null) {
            return canonical;
        }
        for (Seniority seniority : Seniority.values()) {
            String tokenKey = seniority.value().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            String nameKey = seniority.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (key.equals(tokenKey) || key.equals(nameKey)) {
                return seniority.value();
            }
        }
        return null;
    }

    /** An unknown spelling is null, never {@code other}: the report counts "not recorded" apart. */
    static String gender(String value) {
        String trimmed = text(value);
        if (trimmed == null) {
            return null;
        }
        return GENDER_SPELLINGS.get(trimmed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""));
    }

    /**
     * Only an exact equivalent folds ({@link NoticePeriod#ofPair}); anything else, "negotiable"
     * included, is null, so the row keeps what it held rather than a figure nobody stated.
     */
    static String noticePeriod(String value) {
        String trimmed = text(value);
        if (trimmed == null) {
            return null;
        }
        String key = trimmed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (NO_NOTICE_SPELLINGS.contains(key)) {
            return NoticePeriod.NONE.value();
        }
        Matcher stated = STATED_PERIOD.matcher(key);
        if (!stated.matches()) {
            return null;
        }
        Integer count = COUNT_WORDS.get(stated.group(1));
        NoticePeriod period = NoticePeriod.ofPair(
                count != null ? count : Integer.valueOf(stated.group(1)), NOTICE_UNITS.get(stated.group(2)));
        return period == null ? null : period.value();
    }

    private static final Set<String> NO_NOTICE_SPELLINGS = Set.of("none", "nil", "nonotice", "nonoticeperiod",
            "immediate", "immediately", "availableimmediately", "asap", "0");

    private static final Pattern STATED_PERIOD = Pattern.compile(
            "^(\\d{1,3}|one|two|three|four|six|eight|twelve|thirty|sixty|ninety)"
                    + "(m|mo|mos|mth|mths|month|months|w|wk|wks|week|weeks|d|day|days)$");

    private static final Map<String, Integer> COUNT_WORDS = Map.of("one", 1, "two", 2, "three", 3, "four", 4,
            "six", 6, "eight", 8, "twelve", 12, "thirty", 30, "sixty", 60, "ninety", 90);

    private static final Map<String, NoticeUnit> NOTICE_UNITS = Map.ofEntries(
            Map.entry("m", NoticeUnit.MONTHS),
            Map.entry("mo", NoticeUnit.MONTHS),
            Map.entry("mos", NoticeUnit.MONTHS),
            Map.entry("mth", NoticeUnit.MONTHS),
            Map.entry("mths", NoticeUnit.MONTHS),
            Map.entry("month", NoticeUnit.MONTHS),
            Map.entry("months", NoticeUnit.MONTHS),
            Map.entry("w", NoticeUnit.WEEKS),
            Map.entry("wk", NoticeUnit.WEEKS),
            Map.entry("wks", NoticeUnit.WEEKS),
            Map.entry("week", NoticeUnit.WEEKS),
            Map.entry("weeks", NoticeUnit.WEEKS),
            Map.entry("d", NoticeUnit.DAYS),
            Map.entry("day", NoticeUnit.DAYS),
            Map.entry("days", NoticeUnit.DAYS));

    private static final Map<String, String> GENDER_SPELLINGS = Map.ofEntries(
            Map.entry("f", Gender.FEMALE.value()),
            Map.entry("female", Gender.FEMALE.value()),
            Map.entry("woman", Gender.FEMALE.value()),
            Map.entry("w", Gender.FEMALE.value()),
            Map.entry("m", Gender.MALE.value()),
            Map.entry("male", Gender.MALE.value()),
            Map.entry("man", Gender.MALE.value()),
            Map.entry("o", Gender.OTHER.value()),
            Map.entry("other", Gender.OTHER.value()),
            Map.entry("nonbinary", Gender.OTHER.value()),
            Map.entry("nb", Gender.OTHER.value()),
            Map.entry("x", Gender.OTHER.value()));

    private static final Map<String, String> SENIORITY_SPELLINGS = Map.ofEntries(
            Map.entry("board", Seniority.BOARD.value()),
            Map.entry("boardlevel", Seniority.BOARD.value()),
            Map.entry("nedd", Seniority.BOARD.value()),
            Map.entry("csuite", Seniority.C_SUITE.value()),
            Map.entry("clevel", Seniority.C_SUITE.value()),
            Map.entry("executive", Seniority.C_SUITE.value()),
            Map.entry("n", Seniority.C_SUITE.value()),
            Map.entry("nminus1", Seniority.N_MINUS_1.value()),
            Map.entry("n1", Seniority.N_MINUS_1.value()),
            Map.entry("nminusone", Seniority.N_MINUS_1.value()),
            Map.entry("nminus2", Seniority.N_MINUS_2.value()),
            Map.entry("n2", Seniority.N_MINUS_2.value()),
            Map.entry("nminustwo", Seniority.N_MINUS_2.value()),
            Map.entry("nminus3", Seniority.N_MINUS_3.value()),
            Map.entry("n3", Seniority.N_MINUS_3.value()),
            Map.entry("nminusthree", Seniority.N_MINUS_3.value()));
}
