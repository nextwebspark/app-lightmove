package app.lightmove.api.strategy.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** Which roles a people search's job titles are matched against — ContactOut's {@code match_experience}. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum TitleMatch implements ApiValueEnum {

    CURRENT("current"),
    PAST("past"),
    BOTH("both");

    private final String value;

    public static TitleMatch fromValue(String value) {
        return ApiValueEnum.fromValue(TitleMatch.class, value);
    }
}
