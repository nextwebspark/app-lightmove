package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** What an executive's long-term incentive is paid in. {@link #NONE} is a recorded "no LTIP", never "not established". */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum LongTermIncentiveType implements ApiValueEnum {

    OPTIONS("options"),

    RSUS("rsus"),

    CASH("cash"),

    NONE("none");

    private final String value;

    public static LongTermIncentiveType fromValue(String value) {
        return ApiValueEnum.fromValue(LongTermIncentiveType.class, value);
    }
}
