package app.lightmove.api.workspace.constant;

/** Membership is invitation-only, so every membership starts ACTIVE; there is no pending state. */
public enum MemberStatus {

    ACTIVE,

    SUSPENDED,

    /** Kept rather than deleted: projects and audit events still point at this membership. */
    REMOVED;

    /** Only one status grants access to the workspace's data. */
    public boolean grantsAccess() {
        return this == ACTIVE;
    }
}
