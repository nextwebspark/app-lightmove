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

    /**
     * One assistant turn reached the model. Recorded for the reason a contact lookup is: it spends
     * money against the firm's account on a named person's behalf, and the cost has to be
     * attributable after the fact.
     */
    ASSISTANT_TURN_RAN;

    @Override
    public String code() {
        return name();
    }
}
