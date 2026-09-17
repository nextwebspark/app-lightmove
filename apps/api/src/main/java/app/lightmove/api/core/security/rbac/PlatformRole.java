package app.lightmove.api.core.security.rbac;

/** Code-side names for the seeded PLATFORM-scope roles; {@code RbacCatalogTest} keeps the two in step. */
public enum PlatformRole {

    /** LightMove staff: curates the libraries every workspace shares. Grants nothing inside a tenant. */
    SUPER_ADMIN
}
