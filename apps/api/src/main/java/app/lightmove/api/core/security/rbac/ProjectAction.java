package app.lightmove.api.core.security.rbac;

/**
 * Code-side names for the seeded PROJECT-scope actions in {@code app_lm_action}. See
 * {@link WorkspaceAction} for the pattern; {@code RbacCatalogTest} keeps enum and seeds aligned.
 */
public enum ProjectAction {

    PROJECT_EDIT,

    TEAM_MANAGE,

    /**
     * The mandate half of client access, LEAD only ({@code CLIENT_RECORD_MANAGE} is the registry half).
     * Deliberately not {@link #PROJECT_EDIT}: widening that must not admit outsiders to a search.
     */
    CLIENT_ACCESS_MANAGE,

    /** The gate on every project-content GET, held by every seat including a CLIENT. */
    WORK_VIEW,

    /** The writes; staff roles only, never a CLIENT. */
    WORK_EXECUTE
}
