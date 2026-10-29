package app.lightmove.api.candidate.constant;

import java.util.Set;

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

    /** Moved to another Companies stage, on their own or carried by their company's move (#903). */
    TRIAGE_STATUS_CHANGED,

    PROFILE_EDITED,
    CONTACTS_EDITED,

    /** A contact lookup answered — found, or recorded that there was nothing to find. */
    CONTACT_FOUND,

    /** A vendor's research filled in the profile. */
    RESEARCHED,

    /** The AI proposed background or drafted the assessment against a mandate's brief; {@code scope} says which. */
    AI_ASSESSED,

    /** The line carries the note's id, never its text: deleting a note must take the words with it. */
    NOTE_ADDED,
    /** No longer written or read: a note's one line shows it as it stands, and the audit trail keeps each edit. */
    NOTE_EDITED,
    NOTE_REMOVED,
    NOTE_RESTORED,

    /** The line carries the tag's id and its label as it was spelled then; the read prefers today's. */
    TAGGED,
    UNTAGGED,

    /** Who keeps the relationship; the line names the new owner, or none when it was cleared. */
    OWNER_CHANGED,

    DO_NOT_CONTACT_SET,
    DO_NOT_CONTACT_CLEARED,

    /** Put on an outreach sequence; the line names the sequence. Nothing is sent by this alone. */
    OUTREACH_ENROLLED,

    /** One email of a sequence went; the line names the sequence and the step, never the email. */
    EMAIL_SENT,

    /** The person answered a sequence. The reply itself stays in the sender's inbox. */
    EMAIL_REPLIED,

    /** A sequence ended short of its last step; the line carries why. */
    OUTREACH_STOPPED,

    /** A call was booked with the person; the line carries when, and whether they booked it themselves. */
    MEETING_BOOKED,

    /** A call booked in Uncava was moved; the line carries the old and the new time. */
    MEETING_RESCHEDULED,

    /** A call booked in Uncava was cancelled; the line carries when it was to be. */
    MEETING_CANCELLED,

    /** The line carries the document's id and version, never its name: the read names it while it exists. */
    DOCUMENT_ADDED,
    DOCUMENT_VERSION_ADDED,
    DOCUMENT_REMOVED,
    DOCUMENT_VERSION_REMOVED,

    /** V132's free-text next action; no longer written since V133 made a next action a task. */
    NEXT_ACTION_SET,
    CLIENT_VISIBILITY_CHANGED,

    /** The line carries the task's id, title and kind as they were, and who holds it. */
    TASK_CREATED,
    TASK_COMPLETED,
    TASK_REOPENED,

    /** The line names who held the task before and who holds it now. */
    TASK_REASSIGNED,
    TASK_CANCELLED,

    /** A consultant marked their assessment against a mandate's brief reviewed. A draft save writes no line. */
    ASSESSMENT_REVIEWED,

    /**
     * A researcher applied a change a recorded call suggested; {@code reverted} marks an undo, written only while the
     * review offered one. The line names the fields and the call, never the values.
     */
    UPDATED_FROM_CALL;

    /** Kinds still on old rows that no read shows. */
    public static final Set<PersonActivityKind> RETIRED = Set.of(NOTE_EDITED);
}
