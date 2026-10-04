package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** The pool's sortable columns — an allowlist, since each names a fixed SQL ordering. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PoolSortField implements ApiValueEnum {

    NAME("name"),
    LOCATION("location"),

    /** How many positions the person is in. */
    POSITIONS("positions"),

    /** The person's latest timeline line. */
    ACTIVITY("activity");

    private final String value;
}
