package app.lightmove.api.core.security.rbac;

/**
 * The seeded PROJECT-scope roles, orthogonal to the workspace tier. A seat holds one staff role
 * ({@code ProjectTeamService}'s rule, not a constraint), plus CLIENT alongside it for a dual-role person.
 */
public enum ProjectRole {

    /**
     * Owns the mandate: runs the search, seats the team and their roles, decides client access, unlocks
     * the brief. The creator holds it from the start, and a project keeps at least one — several is
     * legal, none is not.
     */
    LEAD,

    /** Executes: sourcing, triage, candidates, notes. */
    RESEARCHER,

    /** The hiring-company contact's seat: WORK_VIEW and nothing else. Granted by attaching a rep. */
    CLIENT
}
