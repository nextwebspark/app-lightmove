package app.lightmove.api.candidate.constant;

/** Whether saving a LinkedIn URL onto a person may take that profile's key in the workspace. */
public enum ProfileClaim {
    /** Nobody else holds the profile, or the URL names none. */
    FREE,
    /**
     * Another person already holds the profile this person's URL names — V95 left the two sharing it.
     * The save goes through and the key stays where it is until the merge tool folds them.
     */
    SHARED,
    /** Another person is this profile; taking it would make two. */
    HELD
}
