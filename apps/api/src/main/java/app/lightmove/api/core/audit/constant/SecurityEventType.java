package app.lightmove.api.core.audit.constant;

/**
 * Infrastructure-level abuse events, not tied to one feature domain. See {@link AuditEventType} for
 * why the ledger's event set is split this way.
 */
public enum SecurityEventType implements AuditEventType {

    RATE_LIMIT_EXCEEDED,

    /**
     * A tool call the assistant made on a user's behalf was refused by the per-call guard.
     *
     * <p>Security rather than project scope: the model chooses the arguments, so a refusal says
     * something about the conversation reaching for data it was not granted, and the mandate it
     * named may be one the actor has no part in. What the refusal was is deliberately not told to
     * the model, so this ledger row is the only place it is recorded in full.
     */
    ASSISTANT_TOOL_DENIED;

    @Override
    public String code() {
        return name();
    }
}
