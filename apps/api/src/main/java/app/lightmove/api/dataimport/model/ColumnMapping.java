package app.lightmove.api.dataimport.model;

import app.lightmove.api.customcolumn.constant.CustomColumnTarget;
import app.lightmove.api.customcolumn.constant.CustomColumnType;
import app.lightmove.api.dataimport.constant.ImportTargetField;

/**
 * What one column of the uploaded sheet becomes. Exactly one of the three outcomes applies, and which
 * one is decided by which fields are set:
 *
 * <ul>
 *   <li>{@code field} set — the column maps onto a built-in field of the row.
 *   <li>{@code customColumnTarget} set — the column becomes, or fills, a custom column on that grid.
 *   <li>neither set — the column is ignored.
 * </ul>
 *
 * <p>{@code customFieldKey} is present when the mapping points at a custom column the project already
 * has, and absent when the import is to define a new one from {@code customLabel}. That is the
 * difference between a second import topping up an existing Ethnicity column and minting a duplicate.
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
