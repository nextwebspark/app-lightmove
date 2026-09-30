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
    AI_ASSESSED
}
