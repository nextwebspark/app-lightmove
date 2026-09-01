package app.lightmove.api.core.audit.constant;

/**
 * The in-app bug reporter's events. See {@link AuditEventType} for why the ledger's event set is
 * split this way.
 *
 * <p>Worth recording despite the feature being a testing aid: the endpoint accepts writes from
 * callers with no session, so the ledger is the only place a burst of them is visible at all.
 */
public enum FeedbackEventType implements AuditEventType {

    /** A report was accepted, whether or not the tracker took it. */
    FEEDBACK_SUBMITTED,

    /** Accepted, then refused by the issue tracker. The reporter is told; this is where the why lives. */
    FEEDBACK_PUBLISH_FAILED;

    @Override
    public String code() {
        return name();
    }
}
