package app.lightmove.api.workspace.constant;

/**
 * Membership is invitation-only, so every membership starts ACTIVE — an admin naming someone (or a
 * creator naming themselves) is the decision, made before the row exists. There is no pending state.
 */
public enum MemberStatus {

    ACTIVE,

    SUSPENDED,

    /** Kept rather than deleted: projects and audit events still point at this membership. */
    REMOVED;

    /** Whether this membership grants access to the workspace's data. Only one status does. */
    public boolean grantsAccess() {
        return this == ACTIVE;
    }
}
