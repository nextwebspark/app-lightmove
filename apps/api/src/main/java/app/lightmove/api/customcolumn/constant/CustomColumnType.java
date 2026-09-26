package app.lightmove.api.customcolumn.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * What a custom column accepts; the value is always stored as entered, so correcting a column's type
 * after an import discards nothing. No option lists by design.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CustomColumnType implements ApiValueEnum {

    TEXT("text"),
    NUMBER("number"),
    DATE("date"),
    BOOLEAN("boolean");

    private final String value;

    public static CustomColumnType fromValue(String value) {
        return ApiValueEnum.fromValue(CustomColumnType.class, value);
    }
}
