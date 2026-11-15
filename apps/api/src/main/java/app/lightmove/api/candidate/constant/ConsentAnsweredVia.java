package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** Whether the person answered on their own page, or a colleague recorded an answer given another way. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ConsentAnsweredVia implements ApiValueEnum {

    PAGE("page"),
    BY_HAND("by_hand");

    private final String value;
}
