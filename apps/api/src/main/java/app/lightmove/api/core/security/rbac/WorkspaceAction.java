package app.lightmove.api.core.security.rbac;

/**
 * Code-side names for the seeded WORKSPACE-scope actions in {@code app_lm_action}.
 *
 * <p>Authorisation asks "may this member perform this action?", never "which role do they hold?" —
 * which roles grant which actions lives in {@code app_lm_role_action} and can change by INSERT, not
 * redeploy. Add a constant here whenever a migration seeds a new action; {@code RbacCatalogTest} keeps
 * the two in step.
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
    CLIENT_RECORD_MANAGE
}
