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
     * Work the mandate: sourcing, triage, candidates, notes. Seeded now so the Project screen's tables
     * arrive with their action already named; nothing consumes it until they do.
     */
    WORK_EXECUTE
}
