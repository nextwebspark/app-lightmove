package app.lightmove.api.core.security.rbac;

/**
 * Code-side names for the seeded PROJECT-scope actions in {@code app_lm_action}. See
 * {@link WorkspaceAction} for the pattern; {@code RbacCatalogTest} keeps enum and seeds aligned.
 */
public enum ProjectAction {

    /** Change the mandate itself: target date, and stage transitions when stages become mutable. */
    PROJECT_EDIT,

    /** Seat and unseat members, change their project roles. */
    TEAM_MANAGE,

    /**
     * Give or withdraw a client representative's read-only view of this mandate — attaching one the
     * registry already holds, minting one and attaching in a single step, or detaching.
     *
     * <p>The mandate half of client access, and held by LEAD alone. Its workspace counterpart is
     * {@code CLIENT_RECORD_MANAGE}, which is ADMIN or MEMBER and covers the registry: who exists as a
     * client contact. A representative created there sees nothing until a lead grants them a mandate.
     *
     * <p>Deliberately not part of {@link #PROJECT_EDIT}. Admitting an outsider to a search is a
     * different decision from moving its target date, and folding them together would hand the first
     * away the moment the second was widened.
     */
    CLIENT_ACCESS_MANAGE,

    /**
     * Read a project's team-only content: its strategy, position brief, sourcing, and future tables.
     * Held by every seated role, including a read-only CLIENT representative — it is the gate on every
     * project-content GET. Distinct from {@link #WORK_EXECUTE} precisely so a client can view without
     * being able to change anything.
     */
    WORK_VIEW,

    /**
     * Work the mandate: the writes — sourcing, triage, candidates, notes. Held by the staff roles
     * (ADMIN/LEAD/RESEARCHER), never a CLIENT, so read access and write access can be granted apart.
     */
    WORK_EXECUTE,

    /**
     * Unlock a locked position brief. Deliberately not part of PROJECT_EDIT: a locked brief is the
     * benchmark downstream scoring rests on, so reopening it is an ADMIN-only decision.
     */
    POSITION_UNLOCK
}
