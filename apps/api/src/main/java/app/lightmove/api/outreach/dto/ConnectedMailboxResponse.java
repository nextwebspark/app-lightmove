package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.constant.MailboxStatus;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.SenderLiveRunCount;
import java.time.Instant;

/**
 * A connected mailbox as its owner sees it. The grant id never leaves the server; {@code bookingLink} is
 * null until the owner's first Start that uses one.
 *
 * @param timeZone         the IANA zone the sending window and the daily cap are read in
 * @param movesOffNylas    whether a reconnect now would move this Nylas mailbox onto our own gateway
 * @param runsStoppedByMove the runs that move would stop, counted only where it is offered
 * @param liveSequences    the sequences the owner's live runs belong to: what a disconnect stops sending
 * @param livePeople       the people those live runs are addressed to
 */
public record ConnectedMailboxResponse(String address, String provider, MailboxStatus status, int dailyCap,
                                       String timeZone, Instant connectedAt, String bookingLink,
                                       boolean movesOffNylas, int runsStoppedByMove,
                                       int liveSequences, int livePeople) {

    public static ConnectedMailboxResponse of(MailboxConnection connection, String bookingLink,
                                              boolean movesOffNylas, int runsStoppedByMove,
                                              SenderLiveRunCount live) {
        return new ConnectedMailboxResponse(connection.getAddress(), connection.getProvider(), connection.getStatus(),
                connection.getDailyCap(), connection.getTimeZone(), connection.getConnectedAt(), bookingLink,
                movesOffNylas,
                runsStoppedByMove,
                (int) live.getSequences(),
                (int) live.getPeople());
    }
}
