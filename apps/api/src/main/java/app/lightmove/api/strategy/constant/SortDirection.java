package app.lightmove.api.strategy.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Which way a chosen sort column runs. Kept separate from {@link CompanySortField} so the client asks
 * for a column and a direction independently — the company table's headers cycle direction without
 * changing which column is active.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum SortDirection implements ApiValueEnum {

    ASC("asc", "ASC"),
    DESC("desc", "DESC");

    private final String value;

    /** The SQL keyword this direction emits — never the caller's string. */
    private final String sqlKeyword;

    public static SortDirection fromValue(String value) {
        return ApiValueEnum.fromValue(SortDirection.class, value);
    }
}
