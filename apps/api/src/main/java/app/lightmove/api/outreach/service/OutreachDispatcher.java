package app.lightmove.api.outreach.service;

import app.lightmove.api.core.tenant.EveryWorkspace;
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
     * is made in the workspace it belongs to.
     */
    public void dispatchAt(Instant now) {
        Instant started = clock.instant();
        workspaces.forEach(workspaceId -> {
            dispatchSequencesAt(now);
            // Moved on by the time the pass has taken so far: a claim stamped with the pass's start would read to
            // every other instance as already that old, and be failed as uncertain while its email was still going.
            dispatchOwnEmailsAt(now.plus(Duration.between(started, clock.instant())));
        });
    }

    private void dispatchSequencesAt(Instant now) {
        sends.stopUncertainClaims(now);
        List<UUID> claimed = sends.claimDue(now);
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
