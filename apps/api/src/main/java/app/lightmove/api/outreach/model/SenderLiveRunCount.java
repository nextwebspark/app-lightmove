package app.lightmove.api.outreach.model;

/** What disconnecting a mailbox would stop: its owner's live runs, as sequences and as people. */
public interface SenderLiveRunCount {

    long getSequences();

    long getPeople();
}
