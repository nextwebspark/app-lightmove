package app.lightmove.api.core.security.rbac;

/** Code-side names for the seeded PLATFORM-scope actions; {@code RbacCatalogTest} keeps the two in step. */
public enum PlatformAction {

    /** Edit, add, archive and import the shared role-template library. */
    TEMPLATE_LIBRARY_MANAGE,

    /** Grant a workspace contact credits by hand. */
    CREDIT_GRANT,

    /** Set the plan and seats of a workspace billed outside Stripe. */
    SUBSCRIPTION_MANAGE
}
