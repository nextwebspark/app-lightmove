package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * How long ago something happened — a person's latest activity, or when they were added. The windows are
 * cumulative ("in the past month" includes the past week); {@link #NEVER} is nothing recorded at all.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PoolRecencyBand implements ApiValueEnum {

    PAST_WEEK("week", 7),
    PAST_MONTH("month", 30),
    PAST_QUARTER("quarter", 90),

    /** Longer ago than {@link #PAST_QUARTER}. */
    OLDER("older", 90),

    NEVER("never", 0);

    private final String value;
    private final int days;
}
