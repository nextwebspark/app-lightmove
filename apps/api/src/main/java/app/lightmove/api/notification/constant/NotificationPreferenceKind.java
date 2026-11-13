package app.lightmove.api.notification.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** One row of Settings → Notifications. Due soon and overdue share one; the digest is its own switch. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum NotificationPreferenceKind implements ApiValueEnum {

    TASK_ASSIGNED("task_assigned"),
    TASK_DUE("task_due"),
    MENTIONED("mentioned"),
    OUTREACH_REPLIED("outreach_replied"),
    OUTREACH_BOUNCED("outreach_bounced"),
    CALL_BOOKED("call_booked"),
    ADDED_TO_TEAM("added_to_team"),
    TASKS_MOVED("tasks_moved"),
    /** A person's request from their privacy page (#923); only an admin receives these. */
    PRIVACY_REQUEST("privacy_request");

    private final String value;
}
