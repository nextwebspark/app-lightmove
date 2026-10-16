package app.uncava.api.candidate.constant;

import app.uncava.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** A consultant's assessment is a draft until they mark it reviewed; any later save makes it a draft again. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ScorecardStatus implements ApiValueEnum {

    DRAFT("draft"),
    REVIEWED("reviewed");

    private final String value;
}
