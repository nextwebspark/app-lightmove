package app.lightmove.api.strategy.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** Kept apart from {@link CompanySortField} so a header can cycle direction without changing column. */
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
