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
            PersonActivityKind.UNMAPPED, PersonActivityKind.STATUS_CHANGED)),
    NOTES("notes", EnumSet.of(PersonActivityKind.NOTE_ADDED, PersonActivityKind.NOTE_EDITED,
            PersonActivityKind.NOTE_REMOVED)),
    CONTACTS("contacts", EnumSet.of(PersonActivityKind.CONTACTS_EDITED, PersonActivityKind.CONTACT_FOUND,
            PersonActivityKind.DO_NOT_CONTACT_SET, PersonActivityKind.DO_NOT_CONTACT_CLEARED)),
    PROFILE("profile", EnumSet.of(PersonActivityKind.PROFILE_EDITED, PersonActivityKind.RESEARCHED,
            PersonActivityKind.AI_ASSESSED)),
    TAGS("tags", EnumSet.of(PersonActivityKind.TAGGED, PersonActivityKind.UNTAGGED,
            PersonActivityKind.OWNER_CHANGED));

    private final String value;
    private final Set<PersonActivityKind> kinds;

    /** The kinds a filter shows; every kind when no group is asked for, a 400 for a group nobody offers. */
    public static Set<PersonActivityKind> kindsOf(String group) {
        TimelineGroup chosen = ApiValueEnum.parse(TimelineGroup.class, group, null, "timeline group");
        return chosen == null ? EnumSet.allOf(PersonActivityKind.class) : chosen.kinds();
    }
}
