package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * An executive's gender for the diversity chapter, recorded or AI-proposed (flagged in {@code aiInferredFields}).
 * Null is "nobody said", not {@link #OTHER}; they are counted apart.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum Gender implements ApiValueEnum {

    FEMALE("female"),

    MALE("male"),

    OTHER("other");

    private final String value;

    public static Gender fromValue(String value) {
        return ApiValueEnum.fromValue(Gender.class, value);
    }
}
