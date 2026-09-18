package app.lightmove.api.core.security.rbac;

/**
 * The three places a role can exist. A WORKSPACE role governs the tenant; a PROJECT role governs one
 * mandate; a PLATFORM role governs what every tenant shares and nothing inside any of them. The
 * assignment tables pin the scope with a composite foreign key, so a role can never be attached at the
 * wrong tier — the schema refuses, not just the service layer.
 */
public enum RoleScope {
    WORKSPACE,
    PROJECT,
    PLATFORM
}
