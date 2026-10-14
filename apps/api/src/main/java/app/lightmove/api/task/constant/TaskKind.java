package app.lightmove.api.task.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** What kind of job a task is, as the modal's chips offer it. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum TaskKind implements ApiValueEnum {

    CALL("call"),
    EMAIL("email"),
    MEETING("meeting"),
    FOLLOW_UP("follow_up"),
    TODO("todo");

    private final String value;
}
