package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** The six swatches a tag is drawn in — palette roles, not colours, so the theme decides the shade. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CandidateTagColour implements ApiValueEnum {

    GREEN("green"),
    ACCENT("accent"),
    NEUTRAL("neutral"),
    VIOLET("violet"),
    ADJACENT("adjacent"),
    INFERRED("inferred");

    private final String value;
}
