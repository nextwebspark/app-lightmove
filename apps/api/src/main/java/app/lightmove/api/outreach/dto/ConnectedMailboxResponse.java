package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.constant.MailboxStatus;
import app.lightmove.api.outreach.model.MailboxConnection;
import java.time.Instant;

/** A connected mailbox as its owner sees it. The grant id never leaves the server. */
public record ConnectedMailboxResponse(String address, String provider, MailboxStatus status, int dailyCap,
                                       Instant connectedAt) {

    public static ConnectedMailboxResponse of(MailboxConnection connection) {
        return new ConnectedMailboxResponse(connection.getAddress(), connection.getProvider(), connection.getStatus(),
                connection.getDailyCap(), connection.getConnectedAt());
    }
}
