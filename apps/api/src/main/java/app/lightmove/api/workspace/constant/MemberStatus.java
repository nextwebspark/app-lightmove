package app.lightmove.api.workspace.constant;

public enum MemberStatus {

    /**
     * Asked to join, waiting on an admin. Carries <b>no access whatsoever</b> — a pending member cannot
     * read a single row of workspace data.
     *
     * <p>That is the whole point of the state. Sharing an employer's email domain is evidence that
     * someone works there; it is not a decision that they should see an executive-search pipeline. An
     * intern, a contractor and a departing employee all hold a company address. A human makes the call.
     *
     * <p>An <i>invited</i> user never passes through here — an admin naming them is the decision.
     */
    PENDING_APPROVAL,

    ACTIVE,

    /** An admin declined the join request. Kept, not deleted, so the same person cannot simply re-ask. */
    REJECTED,

    SUSPENDED,

    /** Kept rather than deleted: projects and audit events still point at this membership. */
    REMOVED;

    /** Whether this membership grants access to the workspace's data. Only one status does. */
    public boolean grantsAccess() {
        return this == ACTIVE;
    }
}
