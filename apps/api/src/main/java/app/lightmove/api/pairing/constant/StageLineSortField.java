package app.lightmove.api.pairing.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * The Companies grid's sortable columns, each token the column's own id in the SPA. A line is a person at a company,
 * so every field is read off the line as the cell draws it. {@code staffOnly} fields are columns a client seat never
 * draws, and sorting by one is refused to it.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum StageLineSortField implements ApiValueEnum {

    COMPANY("name", false),
    EXECUTIVE("executive", false),
    TITLE("executiveTitle", false),
    COUNTRY("country", false),
    CITY("location", false),
    STATUS("executiveStatus", false),
    SECTOR("sector", false),
    REVENUE("revenue", false),
    EMPLOYEES("employees", false),
    FOUNDED("founded", false),
    ADDED("added", false),
    ADDED_BY("addedBy", true),
    NOTES("note", true),
    SOURCE("source", false);

    private final String value;

    private final boolean staffOnly;
}
