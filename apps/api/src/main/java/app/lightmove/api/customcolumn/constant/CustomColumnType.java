package app.lightmove.api.customcolumn.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * What a custom column holds, and therefore how a value entered into it is validated and how the grid
 * aligns it. Four primitives and no option list: a select column needs an options table, an editor for
 * it, and a rule for rows holding an option somebody deleted.
 *
 * <p>The value is always stored as the string it was entered as; the type decides whether that string
 * is <i>accepted</i>, not how it is kept, so correcting a column's type after an import does not
 * silently discard the values already in it.
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
