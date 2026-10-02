package app.lightmove.api.candidate.constant;

/**
 * What happened to a person, as one line of their timeline. Stored by name and held to the same list
 * by V91's CHECK constraint, so a kind added here needs a migration beside it.
 */
public enum PersonActivityKind {

    /** First seen: a mandate brought someone the workspace did not yet know. */
    ADDED_TO_POOL,

    /** A mandate took on someone already known, rather than duplicating them. */
    MAPPED,

    /** A mandate let the person go; the person stays with the workspace. */
    UNMAPPED,

    STATUS_CHANGED,
    PROFILE_EDITED,
    CONTACTS_EDITED,

    /** A contact lookup answered — found, or recorded that there was nothing to find. */
    CONTACT_FOUND,

    /** A vendor's research filled in the profile. */
    RESEARCHED,

    /** The AI enrichment scored the person against a mandate's brief. */
    AI_ASSESSED,

    /** The line carries the note's id, never its text: deleting a note must take the words with it. */
    NOTE_ADDED,
    NOTE_EDITED,
    NOTE_REMOVED,

    /** The line carries the tag's id and its label as it was spelled then; the read prefers today's. */
    TAGGED,
    UNTAGGED,

    /** Who keeps the relationship; the line names the new owner, or none when it was cleared. */
    OWNER_CHANGED,

    DO_NOT_CONTACT_SET,
    DO_NOT_CONTACT_CLEARED,

    /** Put on an outreach sequence; the line names the sequence. Nothing is sent by this alone. */
    OUTREACH_ENROLLED
}
