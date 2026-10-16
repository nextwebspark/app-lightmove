package app.uncava.api.task.constant;

import app.uncava.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** A cancelled task is kept, for the timeline line naming it, and never shown again. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum TaskStatus implements ApiValueEnum {

    OPEN("open"),
    DONE("done"),
    CANCELLED("cancelled");

    private final String value;
}
