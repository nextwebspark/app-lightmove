package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** Where one purpose stands for a person. {@code EXPIRED} is a request nobody answered, or a consent past its date. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ConsentStatus implements ApiValueEnum {

    REQUESTED("requested"),
    GIVEN("given"),
    DECLINED("declined"),
    WITHDRAWN("withdrawn"),
    EXPIRED("expired");

    private final String value;
}
