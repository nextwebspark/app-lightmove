package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.constant.MailboxStatus;
import app.lightmove.api.outreach.model.MailboxConnection;
import java.time.Instant;

/**
 * A connected mailbox as its owner sees it. The grant id never leaves the server; {@code bookingLink} is
 * null until the owner's first Start that uses one.
 */
public record ConnectedMailboxResponse(String address, String provider, MailboxStatus status, int dailyCap,
                                       Instant connectedAt, String bookingLink) {

    public static ConnectedMailboxResponse of(MailboxConnection connection, String bookingLink) {
        return new ConnectedMailboxResponse(connection.getAddress(), connection.getProvider(), connection.getStatus(),
                connection.getDailyCap(), connection.getConnectedAt(), bookingLink);
    }
}
