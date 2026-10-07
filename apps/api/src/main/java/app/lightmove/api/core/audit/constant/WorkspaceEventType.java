package app.lightmove.api.core.audit.constant;

/**
 * Tenancy audit events: the workspace itself, its membership and its own role templates. See
 * {@link AuditEventType} for why the ledger's event set is split this way.
 */
public enum WorkspaceEventType implements AuditEventType {

    WORKSPACE_CREATED,
    WORKSPACE_UPDATED,
    WORKSPACE_DELETED,

    MEMBER_INVITED,
    INVITATION_ACCEPTED,
    INVITATION_REVOKED,

    MEMBER_ROLE_CHANGED,
    MEMBER_REMOVED,

    POSITION_TEMPLATE_CREATED,
    POSITION_TEMPLATE_CUSTOMISED,
    POSITION_TEMPLATE_UPDATED,
    POSITION_TEMPLATE_RESET,
    POSITION_TEMPLATE_DELETED,
    POSITION_TEMPLATE_HIDDEN,
    POSITION_TEMPLATE_SHOWN,
    POSITION_TEMPLATES_IMPORTED,

    MAILBOX_CONNECTED,
    MAILBOX_DISCONNECTED,
    MAILBOX_TIME_ZONE_CHANGED,

    ZOOM_CONNECTED,
    ZOOM_DISCONNECTED,

    API_KEY_CREATED,
    API_KEY_REVOKED,

    OAUTH_GRANT_CREATED,
    OAUTH_GRANT_REVOKED,
    OAUTH_TOKEN_REFRESHED,
    OAUTH_CLIENT_REGISTERED,

    FAIR_USE_REACHED;

    @Override
    public String code() {
        return name();
    }
}
