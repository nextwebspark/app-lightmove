package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** The timeline's filter chips: which kinds of line each one shows. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum TimelineGroup implements ApiValueEnum {

    POSITIONS("positions", EnumSet.of(PersonActivityKind.ADDED_TO_POOL, PersonActivityKind.MAPPED,
            PersonActivityKind.UNMAPPED, PersonActivityKind.STATUS_CHANGED,
            PersonActivityKind.TRIAGE_STATUS_CHANGED, PersonActivityKind.NEXT_ACTION_SET,
            PersonActivityKind.CLIENT_VISIBILITY_CHANGED, PersonActivityKind.TASK_CREATED,
            PersonActivityKind.TASK_COMPLETED, PersonActivityKind.TASK_REOPENED, PersonActivityKind.TASK_REASSIGNED,
            PersonActivityKind.TASK_CANCELLED)),
    NOTES("notes", EnumSet.of(PersonActivityKind.NOTE_ADDED, PersonActivityKind.NOTE_REMOVED,
            PersonActivityKind.NOTE_RESTORED)),
    CONTACTS("contacts", EnumSet.of(PersonActivityKind.CONTACTS_EDITED, PersonActivityKind.CONTACT_FOUND,
            PersonActivityKind.DO_NOT_CONTACT_SET, PersonActivityKind.DO_NOT_CONTACT_CLEARED,
            PersonActivityKind.OUTREACH_ENROLLED, PersonActivityKind.EMAIL_SENT, PersonActivityKind.EMAIL_REPLIED,
            PersonActivityKind.OUTREACH_STOPPED, PersonActivityKind.MEETING_BOOKED,
            PersonActivityKind.MEETING_RESCHEDULED, PersonActivityKind.MEETING_CANCELLED)),
    PROFILE("profile", EnumSet.of(PersonActivityKind.PROFILE_EDITED, PersonActivityKind.RESEARCHED,
            PersonActivityKind.AI_ASSESSED, PersonActivityKind.ASSESSMENT_REVIEWED,
            PersonActivityKind.UPDATED_FROM_CALL)),
    TAGS("tags", EnumSet.of(PersonActivityKind.TAGGED, PersonActivityKind.UNTAGGED,
            PersonActivityKind.OWNER_CHANGED)),
    DOCUMENTS("documents", EnumSet.of(PersonActivityKind.DOCUMENT_ADDED, PersonActivityKind.DOCUMENT_VERSION_ADDED,
            PersonActivityKind.DOCUMENT_REMOVED, PersonActivityKind.DOCUMENT_VERSION_REMOVED));

    private final String value;
    private final Set<PersonActivityKind> kinds;

    /** The kinds a filter shows; every live kind when no group is asked for, a 400 for a group nobody offers. */
    public static Set<PersonActivityKind> kindsOf(String group) {
        TimelineGroup chosen = ApiValueEnum.parse(TimelineGroup.class, group, null, "timeline group");
        return chosen == null ? EnumSet.complementOf(EnumSet.copyOf(PersonActivityKind.RETIRED)) : chosen.kinds();
    }
}
