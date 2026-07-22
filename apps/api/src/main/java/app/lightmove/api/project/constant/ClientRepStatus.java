package app.lightmove.api.project.constant;

/**
 * A client representative's lifecycle. INVITED holds an outstanding portal invitation; ACTIVE once
 * they accept and an account exists; REVOKED when their access is withdrawn — a revoked row is reused
 * rather than duplicated if the same address is invited again.
 */
public enum ClientRepStatus {
    INVITED,
    ACTIVE,
    REVOKED
}
