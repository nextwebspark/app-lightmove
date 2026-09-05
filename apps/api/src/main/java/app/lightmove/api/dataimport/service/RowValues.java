package app.lightmove.api.dataimport.service;

import app.lightmove.api.common.constant.Seniority;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a spreadsheet cell into the value a typed field wants.
 *
 * <p>Every rule here exists because a real export broke without it. A headcount arrives as
 * "1,200" or "1200.0"; a salary as "AED 450,000"; a founding year as "1998.0" because Excel decided
 * the column was numeric. Refusing those would refuse most real files, so they are read rather than
 * rejected — but only where the reading is unambiguous.
 *
 * <p>An unreadable value answers {@code null} rather than throwing. A single unparseable headcount in
 * a thousand-row file is a cell to leave empty, not a reason to fail the row and hide the ninety-nine
 * other fields on it. The fields that genuinely cannot be guessed — a company with no name, a person
 * with no name — are checked by the caller, where failing the row is the right answer.
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
     * A whole number out of whatever a spreadsheet made of it — grouping separators, a currency
     * prefix, a trailing {@code .0} from a numeric cell, all removed. A genuine fraction is rounded,
     * because every field this feeds is a count or a whole-currency-unit figure.
     */
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

    /** A three-letter currency code, or null. "AED 450,000" in a currency column still means AED. */
    static String currency(String value) {
        String trimmed = text(value);
        if (trimmed == null) {
            return null;
        }
        String letters = trimmed.replaceAll("[^A-Za-z]", "");
        return letters.length() == 3 ? letters.toUpperCase(Locale.ROOT) : null;
    }

    /**
     * A seniority as the candidate API spells it, from however the file spelled it.
     *
     * <p>Matched against both the wire token ("N-1") and the enum name ("N_MINUS_1"), plus the
     * spellings files actually carry, because "N minus 1", "n-1" and "CSuite" are all the same rung
     * and none of them is either canonical form.
     */
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
