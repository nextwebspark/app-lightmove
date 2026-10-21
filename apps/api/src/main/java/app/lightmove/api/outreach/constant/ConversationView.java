package app.lightmove.api.outreach.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** The Outreach list's filters: who each shows. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ConversationView implements ApiValueEnum {

    /** Everyone in a queue group. */
    NEEDS("needs"),

    /** Everyone this position wrote to, has an email waiting for, enrolled, met, or holds a task on. */
    ALL("all"),

    /** Everyone with an email still to go, soonest first. */
    SCHEDULED("scheduled"),

    /** Everyone an email went to, latest first. */
    SENT("sent");

    private final String value;
}
