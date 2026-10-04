package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.constant.MailboxStatus;
import app.lightmove.api.outreach.model.MailboxConnection;
import java.time.Instant;

/**
 * A connected mailbox as its owner sees it. The grant id never leaves the server; {@code bookingLink} is
 * null until the owner's first Start that uses one.
 *
 * @param movesOffNylas    whether a reconnect now would move this Nylas mailbox onto our own gateway
 * @param runsStoppedByMove the runs that move would stop, counted only where it is offered
 */
public record ConnectedMailboxResponse(String address, String provider, MailboxStatus status, int dailyCap,
                                       Instant connectedAt, String bookingLink, boolean movesOffNylas,
                                       int runsStoppedByMove) {

    public static ConnectedMailboxResponse of(MailboxConnection connection, String bookingLink,
                                              boolean movesOffNylas, int runsStoppedByMove) {
        return new ConnectedMailboxResponse(connection.getAddress(), connection.getProvider(), connection.getStatus(),
                connection.getDailyCap(), connection.getConnectedAt(), bookingLink, movesOffNylas,
                runsStoppedByMove);
    }
}
