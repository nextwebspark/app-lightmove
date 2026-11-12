package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** Why a person may not be filed again: erased at their request, objecting to processing, or an admin's entry. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum SuppressionReason implements ApiValueEnum {

    ERASED("erased"),
    OBJECTED("objected"),
    MANUAL("manual");

    private final String value;
}
