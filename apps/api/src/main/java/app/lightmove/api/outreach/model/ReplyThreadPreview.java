package app.lightmove.api.outreach.model;

/**
 * What a reply from a consultant's mailbox would do, before it is written: the subject it would carry, and whether it
 * would stop a run of theirs still going in that thread.
 */
public record ReplyThreadPreview(String subject, boolean stopsLiveRun) {}
