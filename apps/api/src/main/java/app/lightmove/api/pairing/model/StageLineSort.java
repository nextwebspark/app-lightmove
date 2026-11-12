package app.lightmove.api.pairing.model;

import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.pairing.constant.StageLineSortField;

/** The grid's one sort: a built-in column, or exactly one of the mandate's own columns, and which way it runs. */
public record StageLineSort(StageLineSortField field, CustomColumnDto customColumn, boolean descending) {

    public StageLineSort {
        if ((field == null) == (customColumn == null)) {
            throw new IllegalArgumentException("A sort names a built-in column or a custom one, never both or neither");
        }
    }

    public boolean sortsBy(StageLineSortField column) {
        return field == column;
    }
}
