package app.lightmove.api.core.security.rbac;

/**
 * The two places a role can exist. A WORKSPACE role governs the tenant; a PROJECT role governs one
 * mandate. The assignment tables pin the scope with a composite foreign key, so a PROJECT role can
 * never be attached to a workspace membership — the schema refuses, not just the service layer.
 */
public enum RoleScope {
    WORKSPACE,
    PROJECT
}
