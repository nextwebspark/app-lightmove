package app.lightmove.api.outreach.service;

import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.constant.MailboxGatewayKind;
import app.lightmove.api.outreach.constant.RecallCalendarStatus;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.MailboxConnected;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.RecallCalendarEvent;
import app.lightmove.api.outreach.model.RecallCalendarReleased;
import app.lightmove.api.outreach.model.RecallCalendarSpec;
import app.lightmove.api.outreach.model.RecallWebhookDelivery;
import app.lightmove.api.outreach.model.RecallWebhookNotice;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import app.lightmove.api.workspace.constant.CalendarSync;
import app.lightmove.api.workspace.model.CalendarSyncChanged;
import app.lightmove.api.workspace.service.WorkspaceSettingsService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
 * One Recall calendar per direct mailbox, while its workspace syncs calendars through Recall: made when the mailbox
 * connects, handed the new refresh token when it reconnects, deleted when it disconnects, loses access, or its
 * workspace moves to {@code DIRECT}. Recall is called outside any transaction; a call that fails leaves the row
 * without a calendar, and the reply poll makes it again. Recall reporting a calendar {@code disconnected} is a refresh
 * the provider refused, and marks the mailbox for reconnecting; Recall reporting its events synced is a read of what
 * changed, kept as meetings exactly as a direct read would be.
 */
@Component
@Slf4j
public class RecallCalendars {

    static final int MAX_OWED_PER_POLL = 10;

    /** After this many failed creates the calendar is left until the mailbox reconnects or the sync is switched. */
    static final int MAX_ATTEMPTS = 5;

    private final RecallCalendarApi recall;
    private final MailboxConnectionRepository mailboxes;
    private final ProviderCredentialsResolver credentials;
    private final SecretCipher cipher;
    private final WorkspaceSettingsService workspaces;
    private final MeetingSync meetings;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /** Its own transactions, never the caller's: run after a commit, the finished one is still bound to the thread. */
    RecallCalendars(RecallCalendarApi recall, MailboxConnectionRepository mailboxes,
                    ProviderCredentialsResolver credentials, SecretCipher cipher, WorkspaceSettingsService workspaces,
                    MeetingSync meetings, PlatformTransactionManager transactionManager, Clock clock) {
        this.recall = recall;
        this.mailboxes = mailboxes;
        this.credentials = credentials;
        this.cipher = cipher;
        this.workspaces = workspaces;
        this.meetings = meetings;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onConnected(MailboxConnected connected) {
        mailboxes.findById(connected.mailboxConnectionId())
                .ifPresent(mailbox -> reconcile(mailbox, true, null));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onReleased(RecallCalendarReleased released) {
        deleteQuietly(released.calendarId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCalendarSyncChanged(CalendarSyncChanged changed) {
        mailboxes.findByWorkspaceIdAndGateway(changed.workspaceId(), MailboxGatewayKind.DIRECT)
                .forEach(mailbox -> reconcile(mailbox, false, changed.calendarSync()));
    }

    /** The reply poll's: calendars a failed call left unmade, each tried again once its backoff has passed. */
    public void createOwed() {
        if (recall.isOffered()) {
            mailboxes.findOwedRecallCalendars(MAX_ATTEMPTS, clock.instant(), MAX_OWED_PER_POLL)
                    .forEach(mailbox -> reconcile(mailbox, false, CalendarSync.RECALL));
        }
    }

    /** A Recall webhook delivery; refused by the API when its signature does not verify. */
    public void receive(RecallWebhookDelivery delivery) {
        for (RecallWebhookNotice notice : recall.notices(delivery)) {
            if (mailboxes.findByRecallCalendarId(notice.calendarId()).isEmpty()) {
                continue;
            }
            switch (notice) {
                case RecallWebhookNotice.CalendarStateChanged changed -> checkAccess(changed.calendarId());
                case RecallWebhookNotice.CalendarEventsChanged changed ->
                        syncEvents(changed.calendarId(), recall.eventsUpdatedSince(changed.calendarId(),
                                changed.since()));
            }
        }
    }

    private void checkAccess(String calendarId) {
        if (recall.status(calendarId) == RecallCalendarStatus.DISCONNECTED) {
            transactions.executeWithoutResult(status -> mailboxes.findByRecallCalendarId(calendarId)
                    .forEach(mailbox -> {
                        mailbox.markAccessWithdrawn();
                        mailbox.releaseRecallCalendar();
                        log.info("Recall lost access to the calendar of mailbox {}; it needs reconnecting",
                                mailbox.getId());
                    }));
            deleteQuietly(calendarId);
        }
    }

    /** A moved event is kept again; a deleted one, or one that stopped being a call, goes. */
    private void syncEvents(String calendarId, List<RecallCalendarEvent> changed) {
        List<CalendarEvent> kept = new ArrayList<>();
        List<String> gone = new ArrayList<>();
        for (RecallCalendarEvent event : changed) {
            CalendarEvent read = RecallEventReading.eventOf(event);
            if (read != null) {
                kept.add(read);
            } else {
                String key = RecallEventReading.keyOf(event);
                if (key != null) {
                    gone.add(key);
                }
            }
        }
        transactions.executeWithoutResult(status -> mailboxes.findByRecallCalendarId(calendarId).stream()
                .filter(MailboxConnection::canSend)
                .forEach(mailbox -> {
                    meetings.applyAll(mailbox, kept);
                    gone.forEach(eventId -> meetings.remove(mailbox, eventId));
                }));
    }

    /**
     * {@code newRefreshToken}: the mailbox was just (re)connected, so a calendar it already has is handed it.
     * {@code calendarSync}: the workspace's, where the caller already knows it; null reads it.
     */
    void reconcile(MailboxConnection mailbox, boolean newRefreshToken, CalendarSync calendarSync) {
        UUID mailboxConnectionId = mailbox.getId();
        String held = mailbox.getRecallCalendarId();
        if (!isWanted(mailbox, calendarSync)) {
            if (held != null) {
                release(mailboxConnectionId, held);
            }
            return;
        }
        if (held != null && !newRefreshToken) {
            return;
        }
        Optional<RecallCalendarSpec> spec = specOf(mailbox);
        if (spec.isEmpty()) {
            log.warn("No OAuth app resolves for mailbox {}; its Recall calendar waits", mailboxConnectionId);
            if (held == null) {
                recordFailure(mailbox);
            }
            return;
        }
        try {
            if (held == null) {
                keep(mailbox, recall.create(spec.get()));
            } else {
                recall.update(held, spec.get());
            }
        } catch (VendorException failed) {
            if (held == null) {
                recordFailure(mailbox);
            } else if (failed.getKind() == VendorFailureKind.NOT_FOUND) {
                forget(mailboxConnectionId, held);
            }
            log.info("Recall calendar for mailbox {} was not {}; the poll tries again", mailboxConnectionId,
                    held == null ? "made" : "updated", failed);
        }
    }

    private boolean isWanted(MailboxConnection mailbox, CalendarSync knownSync) {
        if (!recall.isOffered() || !mailbox.isDirect() || !mailbox.canSend()) {
            return false;
        }
        CalendarSync calendarSync = knownSync != null ? knownSync : workspaces.calendarSyncOf(mailbox.getWorkspaceId());
        return calendarSync == CalendarSync.RECALL;
    }

    /** V103's backoff, so a mailbox whose calendar always fails never holds the poll's place in line. */
    private void recordFailure(MailboxConnection mailbox) {
        String grantId = mailbox.getGrantId();
        Instant now = clock.instant();
        transactions.executeWithoutResult(status -> mailboxes.findById(mailbox.getId())
                .filter(fresh -> grantId.equals(fresh.getGrantId()))
                .ifPresent(fresh -> fresh.markRecallCalendarFailed(now,
                        MeetingBackfill.retryWaitAfter(fresh.getRecallCalendarAttempts() + 1))));
    }

    private Optional<RecallCalendarSpec> specOf(MailboxConnection mailbox) {
        IntegrationProvider provider = mailbox.integrationProvider();
        String platform = switch (provider) {
            case GOOGLE -> "google_calendar";
            case MICROSOFT -> "microsoft_outlook";
            case ZOOM -> null;
        };
        if (platform == null) {
            return Optional.empty();
        }
        return credentials.resolve(mailbox.getWorkspaceId(), provider).map(app -> new RecallCalendarSpec(platform,
                app.clientId(), app.clientSecret(),
                cipher.decrypt(mailbox.getRefreshTokenEncrypted(), mailbox.refreshTokenContext()),
                mailbox.getAddress()));
    }

    /** A reconnect or a second poll may have raced this create: the row keeps one calendar, the other is deleted. */
    private void keep(MailboxConnection mailbox, String created) {
        String grantId = mailbox.getGrantId();
        Boolean kept = transactions.execute(status -> mailboxes.findById(mailbox.getId())
                .filter(fresh -> grantId.equals(fresh.getGrantId()) && fresh.getRecallCalendarId() == null)
                .map(fresh -> {
                    fresh.holdRecallCalendar(created);
                    return true;
                })
                .orElse(false));
        if (!Boolean.TRUE.equals(kept)) {
            deleteQuietly(created);
        }
    }

    private void release(UUID mailboxConnectionId, String calendarId) {
        forget(mailboxConnectionId, calendarId);
        deleteQuietly(calendarId);
    }

    private void forget(UUID mailboxConnectionId, String calendarId) {
        transactions.executeWithoutResult(status -> mailboxes.findById(mailboxConnectionId)
                .filter(fresh -> calendarId.equals(fresh.getRecallCalendarId()))
                .ifPresent(MailboxConnection::releaseRecallCalendar));
    }

    /** Our row is already past it; a calendar Recall keeps reads nothing we store, and its token is still revocable. */
    private void deleteQuietly(String calendarId) {
        if (!recall.isOffered()) {
            return;
        }
        try {
            recall.delete(calendarId);
        } catch (RuntimeException failed) {
            log.warn("Could not delete Recall calendar {}", calendarId, failed);
        }
    }
}
