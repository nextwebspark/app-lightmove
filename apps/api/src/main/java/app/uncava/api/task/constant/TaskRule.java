package app.uncava.api.task.constant;

import app.uncava.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** The outreach events that file a task by themselves; each can be switched off per workspace. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum TaskRule implements ApiValueEnum {

    REPLY("reply"),
    BOUNCE("bounce"),
    BOOKED_CALL("booked_call");

    private final String value;
}
