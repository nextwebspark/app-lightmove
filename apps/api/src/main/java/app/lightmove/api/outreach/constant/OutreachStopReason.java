package app.lightmove.api.outreach.constant;

/**
 * Why a sequence stopped short. A reply or a bounce is the enrollment's status, not a stop reason: these
 * are the runs that ended because somebody, or the send-time re-check, said nothing more may go.
 */
public enum OutreachStopReason {

    /** A consultant pressed Stop. */
    MANUAL,
    DO_NOT_CONTACT,

    /** The person's status says they are out of the running. */
    LEFT_THE_RUNNING,

    /** Removed from the position since they were enrolled. */
    UNMAPPED,

    /** The address the sequence was writing to is no longer on the person's ledger. */
    ADDRESS_REMOVED,

    /** The sender's mailbox was disconnected, or the provider withdrew access to it. */
    MAILBOX_INACTIVE,

    /**
     * The sender reconnected through another gateway, which may not read the thread the run was replying in,
     * so a reply could go unseen and a follow-up go after it.
     */
    MAILBOX_MOVED,

    /** No longer written: a direct mailbox's link now opens Uncava's own page. V110's CHECK still allows it. */
    BOOKING_LINK_UNAVAILABLE,

    /** The mail service refused the email; a send is never retried. */
    SEND_FAILED,

    /** A send was under way when its dispatcher died, so nobody knows whether it went. Never resent. */
    SEND_UNCERTAIN
}
