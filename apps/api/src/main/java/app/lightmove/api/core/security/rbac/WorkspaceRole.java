package app.lightmove.api.core.security.rbac;

/**
 * Code-side names for the seeded WORKSPACE-scope roles in {@code app_lm_role}.
 *
 * <p>The database is the source of truth for what a role <i>grants</i> (see {@code app_lm_role_action});
 * this enum exists so code can name a role without a string literal. {@code RbacCatalogTest} fails the
 * build if these constants and the seeds ever drift apart.
 *
 * <p>Scoped to one workspace, never global — authority always answers "where?". A member may hold
 * several roles; permissions are the union of their roles' actions.
 */
public enum WorkspaceRole {

    /** Governance: settings, billing, membership — and implicit project-admin on every project. */
    ADMIN,

    /** Staff. Creates projects (becoming their project-ADMIN) and works the ones they are seated on. */
    MEMBER,

    /**
     * A hiring-company contact, not staff. Groundwork only: the role is seeded with no actions, nothing
     * grants it yet, and every staff-facing path refuses it. The client portal arrives in a later phase.
     */
    CLIENT;

    public String authority() {
        return "ROLE_" + name();
    }
}
