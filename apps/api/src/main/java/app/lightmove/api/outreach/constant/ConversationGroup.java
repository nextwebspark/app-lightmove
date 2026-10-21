package app.lightmove.api.outreach.constant;

/** Where an executive sits in the Outreach page's action queue, in the order the queue draws the groups. */
public enum ConversationGroup {
    /** An open task the reply rule filed. */
    NEEDS_REPLY,

    /** Any other open task on someone already written to. */
    FOLLOW_UP,

    /** An open task on someone this position never wrote to. */
    PREPARATION,

    /** A bounce task, or a run a send or mailbox failure stopped with nothing sent since. */
    ATTENTION
}
