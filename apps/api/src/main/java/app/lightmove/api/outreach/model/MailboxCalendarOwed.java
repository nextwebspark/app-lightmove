package app.lightmove.api.outreach.model;

import java.util.UUID;

/** A mailbox was connected or reconnected, so its calendar is read once it commits. */
public record MailboxCalendarOwed(UUID mailboxConnectionId) {}
