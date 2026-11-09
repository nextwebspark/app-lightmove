package app.lightmove.api.workspace.constant;

/**
 * Why an outstanding invitation was withdrawn rather than accepted. Each carries which invitations it reaches and the
 * key its {@code INVITATION_REVOKED} audit line records, which the audit log phrases.
 */
public enum InvitationWithdrawal {

    /** A client contact's invitation cancelled from the record. */
    CANCELLED(false, "cancelled"),

    /** Their inviter's membership ended: every invitation they sent, client contacts' included. */
    INVITER_REMOVED(false, "inviter_removed"),

    /** Their inviter stopped being an admin: staff invitations only, since a member still adds client contacts. */
    INVITER_DEMOTED(true, "inviter_demoted");

    private final boolean staffOnly;
    private final String auditKey;

    InvitationWithdrawal(boolean staffOnly, String auditKey) {
        this.staffOnly = staffOnly;
        this.auditKey = auditKey;
    }

    public boolean reachesClientInvitations() {
        return !staffOnly;
    }

    public String auditKey() {
        return auditKey;
    }

    /** The withdrawal an audit line recorded, or null for one written before reasons were. */
    public static InvitationWithdrawal fromAuditKey(String key) {
        for (InvitationWithdrawal withdrawal : values()) {
            if (withdrawal.auditKey.equals(key)) {
                return withdrawal;
            }
        }
        return null;
    }
}
