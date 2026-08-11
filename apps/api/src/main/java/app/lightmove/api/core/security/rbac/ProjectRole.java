package app.lightmove.api.core.security.rbac;

/**
 * Code-side names for the seeded PROJECT-scope roles in {@code app_lm_role}.
 *
 * <p>Orthogonal to the workspace tier — a workspace MEMBER may hold LEAD on one project and RESEARCHER
 * on another. A seat holds <b>one</b> staff role, LEAD or RESEARCHER, enforced in {@code ProjectService}
 * rather than by a constraint: the assignment table still models a set, so admitting a second staff role
 * later is a code change. CLIENT is the exception and sits outside that rule — it comes from attaching a
 * client representative, so a dual-role person holds it <i>alongside</i> their staff role. A seat's
 * permissions are the union of its roles' actions.
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
