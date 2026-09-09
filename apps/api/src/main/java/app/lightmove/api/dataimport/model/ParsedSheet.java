package app.lightmove.api.dataimport.model;

import java.util.List;

/**
 * An uploaded spreadsheet as a table of strings: one header row, then the data rows.
 *
 * <p>Everything is a string on purpose — the same column arrives as a number in one export, as text
 * with a thousands separator in the next, and as a date Excel reformatted in the third — so parsing
 * happens once, where a value is written into a field that has a type. Every row is padded to the
 * header's width, so a reader can index by column position without checking length.
 */
public record ParsedSheet(
        List<SheetColumn> columns,
        List<List<String>> rows
) {

    public ParsedSheet {
        columns = List.copyOf(columns);
        rows = rows.stream().map(List::copyOf).toList();
    }

    public int rowCount() {
        return rows.size();
    }

    /** The value at one column of one row, or {@code null} when the cell is blank or absent. */
    public String cell(List<String> row, int columnIndex) {
        if (columnIndex < 0 || columnIndex >= row.size()) {
            return null;
        }
        String value = row.get(columnIndex);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
