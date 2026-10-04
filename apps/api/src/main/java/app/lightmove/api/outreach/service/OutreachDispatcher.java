package app.lightmove.api.outreach.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The timer behind outreach: every minute it sends what is due, and every quarter hour it asks each
 * listening thread whether anyone answered, for the replies a webhook never delivered, and reads any
 * calendar still owed its first read. Safe on several
 * instances at once — see {@code OutreachEnrollmentClaims}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutreachDispatcher {

    private final OutreachSendService sends;
    private final OutreachInboxService inbox;
    private final MeetingBackfill calendars;
    private final RecallCalendars recallCalendars;
    private final MailboxGateway gateway;
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

    /** One pass. The timer calls it with the clock; tests call it with the moment they arranged. */
    public void dispatchAt(Instant now) {
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
}
