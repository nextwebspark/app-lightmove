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
     * Work the mandate: read its strategy and position brief, sourcing, triage, candidates, notes.
     * Held by every project role, so it is the gate for reading a project's team-only content; the
     * Project screen's own tables will consume it too as they arrive.
     */
    WORK_EXECUTE,

    /**
     * Unlock a locked position brief. Deliberately not part of PROJECT_EDIT: a locked brief is the
     * benchmark downstream scoring rests on, so reopening it is an ADMIN-only decision.
     */
    POSITION_UNLOCK
}
