package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * An executive's gender, for the report's diversity chapter.
 *
 * <p>Recorded by a researcher, or proposed from a researched profile by {@code CandidateBackgroundProposer}
 * and flagged in {@code Candidate.aiInferredFields} until a researcher's edit changes it.
 *
 * <p>Absent is not {@link #OTHER}: a null column means nobody said or confirmed one, {@code OTHER}
 * means somebody did. The two are counted separately for that reason.
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
