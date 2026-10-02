package app.lightmove.api.outreach.model;

import java.util.UUID;

/**
 * A mailbox was connected or reconnected. Once it commits, its calendar is read and a booking page a
 * reconnect left without one is made again — never by a public request.
 */
public record MailboxConnected(UUID mailboxConnectionId) {}
