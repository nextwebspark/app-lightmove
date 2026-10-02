package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.constant.MailboxStatus;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.MailboxCalendarOwed;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
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
 * again from the reply poll for any calendar whose read failed or never ran.
 */
@Component
@Slf4j
public class MeetingBackfill {

    static final Duration REACH = Duration.ofDays(90);

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
    void onConnected(MailboxCalendarOwed owed) {
        syncCalendar(owed.mailboxConnectionId());
    }

    public void syncOwed() {
        mailboxes.findByStatusAndCalendarSyncedAtIsNull(MailboxStatus.ACTIVE)
                .forEach(mailbox -> syncCalendar(mailbox.getId()));
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
            log.warn("Could not read the calendar of mailbox {} at the mail service; the poll tries again",
                    mailboxConnectionId, failed);
            return;
        }
        // A reconnect between the read and this write brought a different grant: its own read is owed.
        transactions.executeWithoutResult(status -> mailboxes.findById(mailboxConnectionId)
                .filter(fresh -> grantId.equals(fresh.getGrantId()))
                .ifPresent(fresh -> {
                    events.forEach(event -> meetings.apply(fresh, event));
                    fresh.markCalendarSynced(now);
                }));
    }
}
