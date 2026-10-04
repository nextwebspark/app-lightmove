package app.lightmove.api.dataimport.model;

import java.util.List;

/**
 * An uploaded spreadsheet as strings, parsed only where a value meets a typed field. Every row is
 * padded to the header's width.
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

    /** {@code null} when the cell is blank or absent. */
    public String cell(List<String> row, int columnIndex) {
        if (columnIndex < 0 || columnIndex >= row.size()) {
            return null;
        }
        String value = row.get(columnIndex);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
