package app.lightmove.api.dataimport.model;

import java.util.List;

/**
 * An uploaded spreadsheet as a table of strings: one header row, then the data rows.
 *
 * <p>Everything is a string on purpose. A spreadsheet cell has no type worth trusting — the same
 * column arrives as a number in one export, as text with a thousands separator in the next, and as a
 * date Excel decided to reformat in the third. Parsing happens once, at the point a value is written
 * into a field that has a type, where the field says what the value has to be.
 *
 * <p>Every row is padded to the header's width, so a reader can index a row by column position
 * without checking its length. A short row in the source is a row whose trailing cells were empty.
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

    /**
     * The value at one column of one row, or {@code null} when the cell is blank.
     *
     * <p>Blank and absent are one answer here rather than two: an empty cell in a spreadsheet says
     * nothing about the field, which is exactly what a null says to every caller downstream.
     */
    public String cell(List<String> row, int columnIndex) {
        if (columnIndex < 0 || columnIndex >= row.size()) {
            return null;
        }
        String value = row.get(columnIndex);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
