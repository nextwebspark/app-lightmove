package app.lightmove.api.project.constant;

/** A representative's lifecycle; a REVOKED row is reused, not duplicated, on a re-invite. */
public enum ClientRepStatus {
    INVITED,
    ACTIVE,
    REVOKED
}
