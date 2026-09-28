package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** How strongly the nationality classifier's evidence supports its category; only {@link #HIGH} fills the field. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum NationalityConfidence implements ApiValueEnum {

    HIGH("high"),

    MEDIUM("medium"),

    LOW("low");

    private final String value;

    public static NationalityConfidence fromValue(String value) {
        return ApiValueEnum.fromValue(NationalityConfidence.class, value);
    }
}
