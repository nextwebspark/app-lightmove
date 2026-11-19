package app.lightmove.api.outreach.service;

import app.lightmove.api.core.tenant.EveryWorkspace;
import app.lightmove.api.core.tenant.TenantResolvers;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The timer behind outreach: every minute it sends what is due — sequences' steps, then consultants' own emails —
 * and every quarter hour it asks each
 * listening thread whether anyone answered, for the replies a webhook never delivered, and reads any
 * calendar still owed its first read. Safe on several
 * instances at once — see {@code OutreachEnrollmentClaims}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutreachDispatcher {

    private final OutreachSendService sends;
    private final OutreachMessageSendService ownSends;
    private final OutreachInboxService inbox;
    private final MeetingBackfill calendars;
    private final RecallCalendars recallCalendars;
    private final MailboxGateway gateway;
    private final EveryWorkspace workspaces;
    private final TenantResolvers resolvers;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${lightmove.outreach.dispatch-interval}",
            initialDelayString = "${lightmove.outreach.dispatch-interval}")
    public void dispatch() {
        if (gateway.isOffered()) {
            dispatchAt(clock.instant());
        }
    }

    @Scheduled(fixedDelayString = "${lightmove.outreach.reply-poll-interval}",
            initialDelayString = "${lightmove.outreach.reply-poll-interval}")
    public void pollReplies() {
        if (gateway.isOffered()) {
            inbox.pollListeningThreads(clock.instant());
            calendars.syncOwed();
            recallCalendars.createOwed();
        }
    }

    /**
     * One pass. The timer calls it with the clock; tests call it with the moment they arranged. A workspace at a time:
     * the runs, their people and the senders' mailboxes are all under row-level security, so each claim and each send
     * is made in the workspace it belongs to — and only in a workspace with something due or a claim still held.
     */
    public void dispatchAt(Instant now) {
        Instant started = clock.instant();
        workspaces.forEach(resolvers.workspacesWithSendsDue(now), workspaceId -> {
            // Every claim is stamped when it is made, not at the pass's start: a stamp that old reads to other instances
            // as an abandoned claim, which once stopped live sequence runs as SEND_UNCERTAIN mid-send.
            Instant claimedAt = now.plus(Duration.between(started, clock.instant()));
            dispatchSequencesAt(now, claimedAt);
            dispatchOwnEmailsAt(claimedAt);
        });
    }

    private void dispatchSequencesAt(Instant now, Instant claimedAt) {
        sends.stopUncertainClaims(now);
        List<UUID> claimed = sends.claimDue(now, claimedAt);
        for (UUID enrollmentId : claimed) {
            try {
                sends.sendClaimed(enrollmentId, now);
            } catch (RuntimeException failed) {
                log.error("Outreach dispatch of enrollment {} failed", enrollmentId, failed);
            }
        }
    }

    private void dispatchOwnEmailsAt(Instant now) {
        List<UUID> claimed;
        try {
            ownSends.failUncertainClaims(now);
            claimed = ownSends.claimDue(now);
        } catch (RuntimeException failed) {
            log.error("Outreach dispatch of consultants' own emails failed", failed);
            return;
        }
        for (UUID messageId : claimed) {
            try {
                ownSends.sendClaimed(messageId, now);
            } catch (RuntimeException failed) {
                log.error("Outreach dispatch of message {} failed", messageId, failed);
            }
        }
    }
}
