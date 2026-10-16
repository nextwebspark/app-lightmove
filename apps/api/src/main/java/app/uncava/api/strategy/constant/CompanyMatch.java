package app.uncava.api.strategy.constant;

import app.uncava.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** Which employers a people search's companies are matched against — ContactOut's {@code company_filter}. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CompanyMatch implements ApiValueEnum {

    CURRENT("current"),
    BOTH("both"),
    PAST_ONLY("past_only");

    private final String value;

    public static CompanyMatch fromValue(String value) {
        return ApiValueEnum.fromValue(CompanyMatch.class, value);
    }
}
