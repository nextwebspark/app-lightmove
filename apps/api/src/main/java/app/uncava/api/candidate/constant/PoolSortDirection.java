package app.uncava.api.candidate.constant;

import app.uncava.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** Which way the Candidates page's sort runs. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PoolSortDirection implements ApiValueEnum {

    ASC("asc"),
    DESC("desc");

    private final String value;
}
