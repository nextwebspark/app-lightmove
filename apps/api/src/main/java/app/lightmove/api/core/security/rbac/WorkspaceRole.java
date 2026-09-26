package app.lightmove.api.core.security.rbac;

import java.util.Collection;

/**
 * Code-side names for the seeded WORKSPACE-scope roles; the database decides what a role grants, and
 * {@code RbacCatalogTest} keeps the two aligned. Permissions are the union of a member's roles.
 */
public enum WorkspaceRole {

    ADMIN,

    MEMBER,

    /** A hiring-company contact, not staff; grants nothing — access is the project CLIENT seat. */
    CLIENT;

    public String authority() {
        return "ROLE_" + name();
    }

    /**
     * True unless {@link #CLIENT} is the only role held — the SPA's {@code isPureClient} rule. From a role
     * set in hand; it does not replace {@code WorkspaceAccess.requireStaff}.
     */
    public static boolean isStaff(Collection<WorkspaceRole> roles) {
        return roles.stream().anyMatch(role -> role != CLIENT);
    }
}
