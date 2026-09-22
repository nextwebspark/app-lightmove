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
    ASSISTANT_TURN_RAN,

    /**
     * A person filed companies the assistant proposed. Beside the {@code TRIAGE_BULK_ADDED} the write
     * itself records, because the two are different facts: that one says rows were written, this one
     * says a proposal was why — and names the turn that made it.
     */
    ASSISTANT_PROPOSAL_ACCEPTED,

    /**
     * An AI Research search ran. Audited like {@code COMPANIES_EXPORTED} rather than like the read it
     * otherwise resembles, and for both of that one's reasons at once: it spends the firm's money on
     * a named person's behalf, and it brings names from outside the product into it.
     *
     * <p>Recorded as a failure with {@code reason: daily_cap} when the workspace's day is already
     * spent, because "the cap bit" is exactly the thing somebody will want to see when they ask why
     * the button stopped working.
     */
    COMPANY_DISCOVERY_RAN;

    @Override
    public String code() {
        return name();
    }
}
