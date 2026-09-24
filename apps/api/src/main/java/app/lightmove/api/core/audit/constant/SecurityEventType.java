package app.lightmove.api.core.audit.constant;

/**
 * Infrastructure-level abuse events, not tied to one feature domain. See {@link AuditEventType} for
 * why the ledger's event set is split this way.
 */
public enum SecurityEventType implements AuditEventType {

    RATE_LIMIT_EXCEEDED;

    @Override
    public String code() {
        return name();
    }
}
