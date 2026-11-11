package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.constant.MailboxStatus;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.SenderLiveRunCount;
import java.time.Instant;

/**
 * A connected mailbox as its owner sees it; the grant id never leaves the server and {@code bookingLink} is null until a
 * first Start uses one. {@code timeZone} is what the window and cap read, {@code sentToday} what the cap counts;
 * {@code runsStoppedByMove}, {@code liveSequences}, {@code livePeople} and {@code scheduledEmails} are what a reconnect
 * or disconnect would stop; {@code reconnectToReadReplies}: a reconnect would grant reply text the consent lacked.
 */
public record ConnectedMailboxResponse(String address, String provider, MailboxStatus status, int dailyCap,
                                       String timeZone, Instant connectedAt, String bookingLink,
                                       boolean movesOffNylas, int runsStoppedByMove,
                                       int liveSequences, int livePeople, boolean readsReplies,
                                       boolean reconnectToReadReplies, long sentToday, long scheduledEmails) {

    public static ConnectedMailboxResponse of(MailboxConnection connection, String bookingLink,
                                              boolean movesOffNylas, int runsStoppedByMove,
                                              SenderLiveRunCount live, boolean readsReplies,
                                              boolean reconnectToReadReplies, long sentToday, long scheduledEmails,
                                              int dailyCap) {
        return new ConnectedMailboxResponse(connection.getAddress(), connection.getProvider(), connection.getStatus(),
                dailyCap, connection.getTimeZone(), connection.getConnectedAt(), bookingLink,
                movesOffNylas,
                runsStoppedByMove,
                (int) live.getSequences(),
                (int) live.getPeople(),
                readsReplies,
                reconnectToReadReplies,
                sentToday,
                scheduledEmails);
    }
}
