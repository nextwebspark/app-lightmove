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
