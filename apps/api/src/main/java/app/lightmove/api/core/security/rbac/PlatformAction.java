package app.lightmove.api.core.security.rbac;

/** Code-side names for the seeded PLATFORM-scope actions; {@code RbacCatalogTest} keeps the two in step. */
public enum PlatformAction {

    /** Edit, add, archive and import the shared role-template library. */
    TEMPLATE_LIBRARY_MANAGE,

    /** Grant a workspace contact credits by hand. */
    CREDIT_GRANT,

    /** Set the plan and seats of a workspace billed outside Stripe. */
    SUBSCRIPTION_MANAGE,

    /** Restore a deleted workspace within its grace period, and read a purged one's deletion certificate. */
    WORKSPACE_LIFECYCLE_MANAGE,

    /** Read the AI prompts and their versions, save a new version and try it. Changes nothing that serves. */
    PROMPT_MANAGE,

    /** Move a prompt's production label: put a version live, or roll back to an earlier one. */
    PROMPT_PROMOTE
}
