package app.lightmove.api.billing.credit.constant;

/** What one ledger line records. {@code ADJUST} carries an overdraft spent while enforcement is off. */
public enum CreditEntryKind {
    GRANT,
    HOLD,
    CAPTURE,
    RELEASE,
    REFUND,
    EXPIRE,
    ADJUST
}
