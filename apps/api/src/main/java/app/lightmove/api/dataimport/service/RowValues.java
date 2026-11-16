package app.lightmove.api.dataimport.service;

import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.common.constant.NoticePeriod;
import app.lightmove.api.common.constant.NoticeUnit;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.common.location.service.Currencies;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.core.text.service.SuppliedText;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /**
     * A fraction is rounded: every field this feeds is a count or a whole-currency figure. Reads a magnitude suffix
     * ("$1.2bn", "5k", "2.5M") — stripping it once saved a billion in revenue as 1. A range ("1001-5000") and a lone
     * decimal comma ("1,5", which could be 1.5 or 15) are null rather than a guess.
     */
    static Long number(String value) {
        String trimmed = text(value);
        if (trimmed == null) {
            return null;
        }
        String lowered = DIGIT_GROUP_SPACE.matcher(trimmed.toLowerCase(Locale.ROOT)).replaceAll("$1");
        if (RANGE.matcher(lowered).find()) {
            return null;
        }
        Matcher figure = FIGURE.matcher(lowered);
        if (!figure.find()) {
            return null;
        }
        BigDecimal amount = decimalOf(figure.group(1));
        if (amount == null) {
            return null;
        }
        String suffix = figure.group(2);
        BigDecimal scaled = suffix == null ? amount : amount.multiply(MAGNITUDES.get(suffix));
        try {
            return scaled.setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException tooLarge) {
            return null;
        }
    }

    private static BigDecimal decimalOf(String digits) {
        boolean negative = digits.startsWith("-");
        String unsigned = negative ? digits.substring(1) : digits;
        String plain;
        int lastComma = unsigned.lastIndexOf(',');
        int lastDot = unsigned.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) {
            plain = lastDot > lastComma
                    ? unsigned.replace(",", "")
                    : unsigned.replace(".", "").replace(',', '.');
        } else if (lastComma >= 0) {
            if (!GROUPED_BY_COMMA.matcher(unsigned).matches()) {
                return null;
            }
            plain = unsigned.replace(",", "");
        } else if (unsigned.indexOf('.') != lastDot) {
            if (!GROUPED_BY_DOT.matcher(unsigned).matches()) {
                return null;
            }
            plain = unsigned.replace(".", "");
        } else {
            plain = unsigned;
        }
        try {
            BigDecimal parsed = new BigDecimal(plain);
            return negative ? parsed.negate() : parsed;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /**
     * The cell's email in the stored form, or null when it is not shaped like one: "n/a", "-" and "TBC" were stored as
     * addresses and then matched every other row carrying the same placeholder.
     */
    static String email(String value) {
        String trimmed = text(value, 320);
        if (trimmed == null) {
            return null;
        }
        String normalised = EmailAddressValidator.normalise(trimmed.replaceFirst("(?i)^mailto:", ""));
        return EmailAddressValidator.hasAddressShape(normalised) ? normalised : null;
    }

    /** A member profile in its canonical spelling ("linkedin.com/in/x" included); anything else as the cell had it. */
    static String linkedinProfile(String value) {
        String trimmed = text(value, 500);
        String slug = LinkedInUrls.profileSlugOrNull(SuppliedText.browsableUrlOrNull(trimmed));
        return slug == null ? trimmed : LinkedInUrls.profileUrlOf(slug);
    }

    /** A company page with its scheme restored, so its slug can be read for research; anything else as the cell had it. */
    static String linkedinCompany(String value) {
        String trimmed = text(value, 500);
        String browsable = SuppliedText.browsableUrlOrNull(trimmed);
        return LinkedInUrls.companySlugOrNull(browsable) == null ? trimmed : browsable;
    }

    /** An ISO 4217 code, or null rather than a guess; "AED 450,000" still means AED. */
    static String currency(String value) {
        String trimmed = text(value);
        if (trimmed == null) {
            return null;
        }
        String code = trimmed.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
        return Currencies.isKnownCode(code) ? code : null;
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

    private static final Pattern DIGIT_GROUP_SPACE = Pattern.compile("(\\d)[\\s\u00a0'](?=\\d{3}(?!\\d))");

    private static final Pattern RANGE = Pattern.compile("\\d\\s*[-\u2013\u2014]\\s*\\d");

    private static final Pattern FIGURE = Pattern.compile(
            "(-?\\d[\\d.,]*)\\s*(?:(thousand|million|billion|mn|mm|bn|k|m|b)(?![a-z]))?");

    private static final Pattern GROUPED_BY_COMMA = Pattern.compile("^\\d{1,3}(,\\d{3})+$");

    private static final Pattern GROUPED_BY_DOT = Pattern.compile("^\\d{1,3}(\\.\\d{3})+$");

    private static final Map<String, BigDecimal> MAGNITUDES = Map.of(
            "k", BigDecimal.valueOf(1_000L), "thousand", BigDecimal.valueOf(1_000L),
            "m", BigDecimal.valueOf(1_000_000L), "mn", BigDecimal.valueOf(1_000_000L),
            "mm", BigDecimal.valueOf(1_000_000L), "million", BigDecimal.valueOf(1_000_000L),
            "b", BigDecimal.valueOf(1_000_000_000L), "bn", BigDecimal.valueOf(1_000_000_000L),
            "billion", BigDecimal.valueOf(1_000_000_000L));

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
