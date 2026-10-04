package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.constant.MailboxGatewayKind;
import app.lightmove.api.outreach.constant.MailboxStatus;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.MailboxConnected;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reads a newly connected calendar once, 90 days either side of today, so the meetings the webhook
 * never saw are there from the start. Run off the request thread after the connection commits, and
 * again from the reply poll for any calendar whose read failed or never ran. A direct calendar nothing pushes
 * changes from — no Recall calendar — is read again when the drawer opens, so a move or delete shows on the next.
 */
@Component
@Slf4j
public class MeetingBackfill {

    static final Duration REACH = Duration.ofDays(90);

    /** One poll reads this many owed calendars at most; the rest wait for the next. */
    static final int MAX_OWED_PER_POLL = 10;

    /** After this many failed reads a calendar is left until its mailbox is reconnected. */
    static final int MAX_ATTEMPTS = 5;

    /** How long a drawer-opening read stands before another opening reads the calendar again. */
    static final Duration FRESH_FOR = Duration.ofMinutes(5);

    /** A drawer-opening read looks this far back; the meetings before it stay as last read. */
    static final Duration RECHECKED_PAST = Duration.ofDays(7);

    /** One opening reads this many teammates' calendars at most. */
    static final int MAX_REFRESHED_PER_OPEN = 10;

    private static final Duration FIRST_RETRY = Duration.ofMinutes(15);
    private static final Duration LONGEST_RETRY = Duration.ofHours(24);

    private final MailboxGateway gateway;
    private final MailboxConnectionRepository mailboxes;
    private final MeetingSync meetings;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Its own transaction, never the caller's: run after the connection commits, the finished one is still
     * bound to the thread, and joining it writes nothing.
     */
    MeetingBackfill(MailboxGateway gateway, MailboxConnectionRepository mailboxes, MeetingSync meetings,
                    PlatformTransactionManager transactionManager, Clock clock) {
        this.gateway = gateway;
        this.mailboxes = mailboxes;
        this.meetings = meetings;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onConnected(MailboxConnected connected) {
        syncCalendar(connected.mailboxConnectionId());
    }

    public void syncOwed() {
        mailboxes.findCalendarsOwed(MailboxStatus.ACTIVE, MAX_ATTEMPTS, clock.instant(),
                        PageRequest.of(0, MAX_OWED_PER_POLL))
                .forEach(mailbox -> syncCalendar(mailbox.getId()));
    }

    /**
     * The drawer's: every direct calendar of the workspace that nothing pushes changes from and that was last read
     * more than {@link #FRESH_FOR} ago is read again, off the request thread. A read that fails changes nothing; the
     * next opening tries again.
     */
    @Async
    public void refreshUnpushed(UUID workspaceId) {
        Instant now = clock.instant();
        mailboxes.findByWorkspaceIdAndGateway(workspaceId, MailboxGatewayKind.DIRECT).stream()
                .filter(mailbox -> mailbox.canSend() && mailbox.getRecallCalendarId() == null
                        && mailbox.getCalendarSyncedAt() != null
                        && mailbox.getCalendarSyncedAt().isBefore(now.minus(FRESH_FOR)))
                .limit(MAX_REFRESHED_PER_OPEN)
                .forEach(mailbox -> refresh(mailbox.getId(), mailbox.getGrantId(), mailbox.getCalendarSyncedAt(), now));
    }

    /**
     * Two drawers opening together both find a calendar stale, and both used to read it and save the mailbox row; the
     * second save met the first's version and failed with a {@code StaleObjectStateException}. The read is claimed
     * first, and the mailbox row is never saved through the entity here.
     */
    private void refresh(UUID mailboxConnectionId, String grantId, Instant lastRead, Instant now) {
        if (mailboxes.claimCalendarRefresh(mailboxConnectionId, grantId, now, now.minus(FRESH_FOR)) == 0) {
            return;
        }
        Instant from = now.minus(RECHECKED_PAST);
        Instant to = now.plus(REACH);
        List<CalendarEvent> events;
        try {
            events = gateway.calendarEvents(grantId, from, to);
        } catch (RuntimeException failed) {
            mailboxes.releaseCalendarRefresh(mailboxConnectionId, now, lastRead);
            log.info("Could not read the calendar of mailbox {} again; the next opening tries", mailboxConnectionId,
                    failed);
            return;
        }
        transactions.executeWithoutResult(status -> mailboxes.findById(mailboxConnectionId)
                .filter(fresh -> grantId.equals(fresh.getGrantId()))
                .ifPresent(fresh -> meetings.replaceWindow(fresh, from, to, events)));
    }

    void syncCalendar(UUID mailboxConnectionId) {
        MailboxConnection mailbox = mailboxes.findById(mailboxConnectionId)
                .filter(MailboxConnection::canSend)
                .orElse(null);
        if (mailbox == null) {
            return;
        }
        String grantId = mailbox.getGrantId();
        Instant now = clock.instant();
        List<CalendarEvent> events;
        try {
            events = gateway.calendarEvents(grantId, now.minus(REACH), now.plus(REACH));
        } catch (RuntimeException failed) {
            recordFailure(mailboxConnectionId, grantId, now, failed);
            return;
        }
        // A reconnect between the read and this write brought a different grant: its own read is owed.
        transactions.executeWithoutResult(status -> mailboxes.findById(mailboxConnectionId)
                .filter(fresh -> grantId.equals(fresh.getGrantId()))
                .ifPresent(fresh -> {
                    meetings.applyAll(fresh, events);
                    fresh.markCalendarSynced(now);
                }));
    }

    private void recordFailure(UUID mailboxConnectionId, String grantId, Instant now, RuntimeException failed) {
        Integer attempts = transactions.execute(status -> mailboxes.findById(mailboxConnectionId)
                .filter(fresh -> grantId.equals(fresh.getGrantId()))
                .map(fresh -> {
                    fresh.markCalendarSyncFailed(now, retryWaitAfter(fresh.getCalendarSyncAttempts() + 1));
                    return fresh.getCalendarSyncAttempts();
                })
                .orElse(null));
        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            log.warn("Gave up reading the calendar of mailbox {} after {} attempts; reconnecting it tries again",
                    mailboxConnectionId, attempts, failed);
        } else {
            log.info("Could not read the calendar of mailbox {} (attempt {}); the poll tries again later",
                    mailboxConnectionId, attempts, failed);
        }
    }

    /** 15 minutes after the first failure, doubling each time, never more than a day. */
    static Duration retryWaitAfter(int attempts) {
        Duration wait = FIRST_RETRY.multipliedBy(1L << Math.min(Math.max(attempts - 1, 0), 10));
        return wait.compareTo(LONGEST_RETRY) > 0 ? LONGEST_RETRY : wait;
    }
}
