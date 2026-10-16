package app.uncava.api.task.constant;

import app.uncava.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** Which door filed a task: the modal, a note's linked follow-up, or a rule acting on an outreach event. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum TaskSource implements ApiValueEnum {

    MANUAL("manual"),
    NOTE("note"),
    RULE("rule");

    private final String value;
}
