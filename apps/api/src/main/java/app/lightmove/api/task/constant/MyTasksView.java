package app.lightmove.api.task.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** The tabs of My tasks. Today and overdue are counted in the viewer's own zone. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum MyTasksView implements ApiValueEnum {

    TODAY("today"),
    OVERDUE("overdue"),
    UPCOMING("upcoming"),
    ASSIGNED_BY_ME("assigned_by_me");

    private final String value;
}
