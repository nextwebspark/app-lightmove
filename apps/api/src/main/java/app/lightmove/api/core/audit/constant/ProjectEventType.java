package app.lightmove.api.core.audit.constant;

/**
 * Project-domain audit events: the mandate, its positions, and its strategy. See
 * {@link AuditEventType} for why the ledger's event set is split this way.
 */
public enum ProjectEventType implements AuditEventType {

    PROJECT_CREATED,
    PROJECT_UPDATED,
    PROJECT_TEAM_CHANGED,

    POSITION_UPDATED,
    POSITION_LOCKED,
    POSITION_UNLOCKED,

    STRATEGY_UPDATED;

    @Override
    public String code() {
        return name();
    }
}
