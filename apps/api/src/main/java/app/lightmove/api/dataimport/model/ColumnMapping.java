package app.lightmove.api.dataimport.model;

import app.lightmove.api.customcolumn.constant.CustomColumnTarget;
import app.lightmove.api.customcolumn.constant.CustomColumnType;
import app.lightmove.api.dataimport.constant.ImportTargetField;

/**
 * What one column becomes: a built-in {@code field}, a custom column ({@code customColumnTarget},
 * with {@code customFieldKey} only when it already exists), or, with neither set, ignored.
 */
public record ColumnMapping(
        int columnIndex,
        String header,
        ImportTargetField field,
        CustomColumnTarget customColumnTarget,
        String customFieldKey,
        String customLabel,
        CustomColumnType customType
) {

    public static ColumnMapping ignored(int columnIndex, String header) {
        return new ColumnMapping(columnIndex, header, null, null, null, null, null);
    }

    public static ColumnMapping onto(int columnIndex, String header, ImportTargetField field) {
        return new ColumnMapping(columnIndex, header, field, null, null, null, null);
    }

    public static ColumnMapping intoCustomColumn(int columnIndex, String header,
                                                 CustomColumnTarget target, String fieldKey,
                                                 String label, CustomColumnType type) {
        return new ColumnMapping(columnIndex, header, null, target, fieldKey, label, type);
    }

    public boolean isIgnored() {
        return field == null && customColumnTarget == null;
    }

    public boolean isCustom() {
        return customColumnTarget != null;
    }
}
