package app.lightmove.api.workspace.domain;

/**
 * What a member may do inside one workspace.
 *
 * <p>Scoped to the workspace, never global: the same person can be an ADMIN of their own firm and a
 * RESEARCHER in a partner's. Authority always answers "where?".
 *
 * <p>Mapped to Spring Security authorities as {@code ROLE_ADMIN} etc. by {@link #authority()}.
 */
public enum WorkspaceRole {

    /** Full control: settings, billing, membership, every project. */
    ADMIN,

    /** Runs mandates. Can create and lead projects. */
    CONSULTANT,

    /** Supports mandates. Contributes to projects they are added to; cannot reshape the workspace. */
    RESEARCHER;

    public String authority() {
        return "ROLE_" + name();
    }
}
