package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** How far an executive would move for a role. Null is "not established"; {@link #NONE} is a recorded no. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum Mobility implements ApiValueEnum {

    NONE("none"),

    COUNTRY("country"),

    REGION("region"),

    INTERNATIONAL("international");

    private final String value;
}
