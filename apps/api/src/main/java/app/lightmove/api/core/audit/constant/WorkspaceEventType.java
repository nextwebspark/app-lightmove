package app.lightmove.api.core.audit.constant;

/**
 * Tenancy audit events: the workspace itself and its membership. See {@link AuditEventType} for why
 * the ledger's event set is split this way.
 */
public enum WorkspaceEventType implements AuditEventType {

    WORKSPACE_CREATED,
    WORKSPACE_UPDATED,
    WORKSPACE_DELETED,

    MEMBER_INVITED,
    INVITATION_ACCEPTED,
    INVITATION_REVOKED,

    MEMBER_ROLE_CHANGED,
    MEMBER_REMOVED;

    @Override
    public String code() {
        return name();
    }
}
