package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** How a tag filter reads its tags: holds any of them, all of them, or none of them. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum TagMatch implements ApiValueEnum {

    ANY("any"),
    ALL("all"),
    NONE("none");

    private final String value;
}
