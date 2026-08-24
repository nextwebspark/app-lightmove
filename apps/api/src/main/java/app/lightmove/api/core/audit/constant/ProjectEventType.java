package app.lightmove.api.core.audit.constant;

/**
 * Project-domain audit events: the mandate, its positions, and its strategy. See
 * {@link AuditEventType} for why the ledger's event set is split this way.
 */
public enum ProjectEventType implements AuditEventType {

    PROJECT_CREATED,
    PROJECT_UPDATED,
    PROJECT_TEAM_CHANGED,

    CLIENT_CREATED,
    CLIENT_UPDATED,
    CLIENT_REP_INVITED,
    CLIENT_REP_ACCEPTED,

    POSITION_UPDATED,
    POSITION_LOCKED,
    POSITION_UNLOCKED,

    STRATEGY_UPDATED,
    STRATEGY_SEARCH_SAVED,
    STRATEGY_SEARCH_RENAMED,
    STRATEGY_SEARCH_DELETED,

    TRIAGE_COMPANY_ADDED,
    TRIAGE_COMPANY_MOVED,
    TRIAGE_BULK_ADDED,

    /**
     * A company written in from the browser extension rather than taken from the Strategy screen.
     * Its own type because the provenance is the interesting part: these rows may carry a snapshot
     * the Apollo pipeline never vouched for.
     */
    TRIAGE_COMPANY_CAPTURED;

    @Override
    public String code() {
        return name();
    }
}
