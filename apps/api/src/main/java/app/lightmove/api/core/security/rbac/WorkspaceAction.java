package app.lightmove.api.core.security.rbac;

/**
 * Code-side names for the seeded WORKSPACE-scope actions in {@code app_lm_action}; which roles grant them is
 * {@code app_lm_role_action}'s. Add one whenever a migration seeds an action — {@code RbacCatalogTest} keeps them in step.
 */
public enum WorkspaceAction {

    /** Settings → General: rename, defaults, branding, deletion. */
    WORKSPACE_MANAGE,

    /** The roster: change a member's roles, remove a member. */
    MEMBER_MANAGE,

    /** Send, resend, revoke and list invitations. */
    MEMBER_INVITE,

    /** Start a mandate — the creator becomes its project-ADMIN (and LEAD). */
    PROJECT_CREATE,

    /** See the workspace's project list. */
    PROJECT_BROWSE,

    /** The client registry — hiring-entity records, not client users. */
    CLIENT_RECORD_MANAGE,

    /** Settings → Templates: the firm's own role templates and its copies of the library's. */
    POSITION_TEMPLATE_MANAGE,

    /** The workspace's people outside any one mandate: their record, notes and timeline. Never a client. */
    CANDIDATE_POOL_MANAGE,

    /** Settings → API keys: one's own personal keys. A workspace key, or a colleague's, also asks {@link #WORKSPACE_MANAGE}. */
    API_KEY_MANAGE,

    /** Settings → Billing: the plan and seats, buying credits, the card and invoices. */
    BILLING_MANAGE,

    /** Erase a person and everything held about them: the one irreversible act on a person, an admin's alone. */
    PERSON_ERASE
}
