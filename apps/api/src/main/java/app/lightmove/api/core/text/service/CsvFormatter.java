package app.lightmove.api.core.text.service;

import java.util.List;

/**
 * RFC 4180 cells for the CSVs the app hands out. Hand-rolled rather than {@code commons-csv}: the
 * same dozen lines, and one idiom for every generated file.
 */
public final class CsvFormatter {

    /**
     * Excel and Sheets evaluate a cell opening with one of these as a formula, so a note reading
     * {@code =HYPERLINK("http://…")} would run on the machine of whoever opened the file. The tab and
     * CR are here because a leading whitespace character is how the naive {@code startsWith("=")}
     * check is bypassed. {@code SpreadsheetReader} undoes exactly this set on the way back in, so the
     * round trip through our own importer is lossless.
     */
    public static final String FORMULA_STARTERS = "=+-@\t\r";

    private CsvFormatter() {}

    /**
     * One row, each value quoted only where it must be: this file is opened in Excel far more often
     * than by a parser, and a value carrying a comma would otherwise be two columns once saved.
     */
    public static String row(List<String> values) {
        return String.join(",", values.stream().map(CsvFormatter::quoted).toList());
    }

    /**
     * A leading apostrophe makes a would-be formula plain text — which also stops a phone number
     * beginning {@code +966} from being read as arithmetic and shown as {@code #NAME?}.
     */
    public static String defused(String value) {
        return !value.isEmpty() && FORMULA_STARTERS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }

    private static String quoted(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }
}
