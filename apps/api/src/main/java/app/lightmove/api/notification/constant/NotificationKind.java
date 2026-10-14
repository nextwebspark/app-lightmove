package app.lightmove.api.notification.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * What a bell row says happened. {@code preference} is the setting that governs it; only an assignment and a
 * mention are ever emailed on their own — the rest wait for the digest.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum NotificationKind implements ApiValueEnum {

    TASK_ASSIGNED("task_assigned", NotificationPreferenceKind.TASK_ASSIGNED, true),
    TASK_DUE_SOON("task_due_soon", NotificationPreferenceKind.TASK_DUE, false),
    TASK_OVERDUE("task_overdue", NotificationPreferenceKind.TASK_DUE, false),
    MENTIONED("mentioned", NotificationPreferenceKind.MENTIONED, true),
    OUTREACH_REPLIED("outreach_replied", NotificationPreferenceKind.OUTREACH_REPLIED, false),
    OUTREACH_BOUNCED("outreach_bounced", NotificationPreferenceKind.OUTREACH_BOUNCED, false),
    CALL_BOOKED("call_booked", NotificationPreferenceKind.CALL_BOOKED, false),
    ADDED_TO_TEAM("added_to_team", NotificationPreferenceKind.ADDED_TO_TEAM, false),
    TASKS_MOVED("tasks_moved", NotificationPreferenceKind.TASKS_MOVED, false);

    private final String value;
    private final NotificationPreferenceKind preference;
    private final boolean emailable;
}
