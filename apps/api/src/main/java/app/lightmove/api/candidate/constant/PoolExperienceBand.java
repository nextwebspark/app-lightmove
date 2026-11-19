package app.lightmove.api.candidate.constant;

import app.lightmove.api.core.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** A band of recorded years of experience; {@link #UNKNOWN} is a person with none recorded. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PoolExperienceBand implements ApiValueEnum {

    UNDER_5("lt5", null, 5),
    FROM_5_TO_10("5to10", 5, 10),
    FROM_10_TO_15("10to15", 10, 15),
    FROM_15_TO_20("15to20", 15, 20),
    FROM_20("20plus", 20, null),
    UNKNOWN("unknown", null, null);

    private final String value;

    /** Inclusive; null for no floor. */
    private final Integer fromYears;

    /** Exclusive; null for no ceiling. */
    private final Integer belowYears;
}
