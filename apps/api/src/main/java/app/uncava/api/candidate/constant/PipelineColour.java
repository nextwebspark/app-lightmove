package app.uncava.api.candidate.constant;

import app.uncava.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * The swatches a stage or a status is drawn in: the tag palette's six roles plus the two the fixed
 * statuses were drawn in before the catalog ({@link #SIGNAL} for out of scope, {@link #RED} for
 * off-limits), so the seeded catalog reads exactly as the grid did.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PipelineColour implements ApiValueEnum {

    GREEN("green"),
    ACCENT("accent"),
    NEUTRAL("neutral"),
    VIOLET("violet"),
    ADJACENT("adjacent"),
    INFERRED("inferred"),
    SIGNAL("signal"),
    RED("red");

    private final String value;
}
