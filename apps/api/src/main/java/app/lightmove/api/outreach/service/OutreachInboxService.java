package app.lightmove.api.outreach.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.outreach.constant.EnrollmentStatus;
import app.lightmove.api.outreach.constant.MessageDirection;
import app.lightmove.api.outreach.constant.MessageKind;
import app.lightmove.api.outreach.model.BookingMade;
import app.lightmove.api.outreach.model.CalendarEventChanged;
import app.lightmove.api.outreach.model.CalendarEventRemoved;
import app.lightmove.api.outreach.model.DeliveryFailure;
import app.lightmove.api.outreach.model.InboundMessage;
import app.lightmove.api.outreach.model.MailboxAccessWithdrawn;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.MailboxEvent;
import app.lightmove.api.outreach.model.OutreachEnrollment;
import app.lightmove.api.outreach.model.OutreachMessage;
import app.lightmove.api.outreach.model.OutreachThread;
import app.lightmove.api.outreach.model.ReplyText;
import app.lightmove.api.outreach.model.ThreadMessage;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import app.lightmove.api.outreach.repository.OutreachEnrollmentRepository;
import app.lightmove.api.outreach.repository.OutreachMessageRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What comes back into a sender's mailbox: a reply or a bounce is a line of the conversation and may end its run, a
 * mailbox whose access was withdrawn stops sending, a calendar event with a mapped person is kept as their meeting,
 * and a booking through a consultant's link ends that person's run. Learned from the mail service's webhook, and from
 * a poll of every thread an email went into lately. Only a thread Uncava started is ever read, and a reply's words
 * only where the mailbox grants them; they are staff-only and never logged.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutreachInboxService {

    /**
     * Who answers for a mail server that could not deliver: their message in the thread is a bounce, not a reply.
     * Exchange Online sends its non-delivery reports from this fixed system mailbox, never from postmaster.
     */
    private static final Set<String> DELIVERY_DAEMONS = Set.of("mailer-daemon", "postmaster",
            "microsoftexchange329e71ec88ae4615bbc36ab6ce41109e");

    /** A read starts this far before the latest line that came back: a message is dated when it was received. */
    private static final Duration READ_OVERLAP = Duration.ofHours(1);

    /**
     * A reply received longer ago than this is history when it is first found — the first read after V145 finds every
     * reply to a thread whose run had already ended, which nobody was ever told of — so it is kept as a line and
     * announced to nobody, unless its run still waits on it.
     */
    private static final Duration ANNOUNCE_WITHIN = Duration.ofDays(1);

    private static final int MAX_ADDRESS = 320;

    private final MailboxGateway gateway;
    private final MailboxConnectionRepository mailboxes;
    private final OutreachEnrollmentRepository enrollments;
    private final OutreachMessageRepository messages;
    private final OutreachOutcomes outcomes;
    private final MeetingSync meetings;
    private final LinkBookings linkBookings;
    private final ReplyTextAccess replyText;
    private final TransactionTemplate transactions;
    private final LightMoveProperties properties;
    private final Clock clock;

    /** One webhook delivery; refused by the gateway when its signature does not verify. */
    public void receive(String signature, byte[] body) {
        Instant now = clock.instant();
        for (MailboxEvent event : gateway.readWebhook(signature, body)) {
            if (event instanceof InboundMessage inbound) {
                mailboxes.findByGrantId(inbound.grantId()).forEach(mailbox -> received(mailbox, inbound, now));
            } else {
                transactions.executeWithoutResult(status -> apply(event, now));
            }
        }
    }

    /**
     * The fallback for a missed webhook, and the only listener for our own gateways: every thread an email went into
     * within the listening window, and every thread a run still waits on.
     */
    public void pollListeningThreads(Instant now) {
        Instant since = now.minus(properties.outreach().replyListenWindow());
        Map<ThreadKey, OutreachThread> listening = new LinkedHashMap<>();
        for (OutreachMessage sent : messages.findLatestSendOfEachThreadSince(since)) {
            listening.putIfAbsent(ThreadKey.of(sent.getWorkspaceId(), sent.getSenderUserId(), sent.getThreadId()),
                    OutreachThread.of(sent));
        }
        for (OutreachEnrollment run : enrollments.findListeningSince(since,
                Set.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED))) {
            listening.putIfAbsent(ThreadKey.of(run.getWorkspaceId(), run.getSenderUserId(), run.getThreadId()),
                    OutreachThread.of(run));
        }
        Map<SenderKey, List<OutreachThread>> bySender = listening.values().stream()
                .collect(Collectors.groupingBy(thread -> new SenderKey(thread.workspaceId(), thread.senderUserId()),
                        LinkedHashMap::new, Collectors.toList()));
        bySender.forEach((sender, threads) -> mailboxes
                .findByWorkspaceIdAndUserId(sender.workspaceId(), sender.userId())
                .filter(MailboxConnection::canSend)
                .ifPresent(mailbox -> threads.stream()
                        .filter(thread -> thread.isReadableBy(mailbox))
                        .forEach(thread -> pollThread(mailbox, thread, now))));
    }

    private void pollThread(MailboxConnection mailbox, OutreachThread thread, Instant now) {
        List<ThreadMessage> written;
        try {
            written = gateway.messagesInThread(mailbox.getGrantId(), thread.threadId(), readFromOf(thread),
                    replyText.readsText(mailbox));
        } catch (RuntimeException failed) {
            log.warn("Could not read outreach thread {} of mailbox {} at the mail service", thread.threadId(),
                    mailbox.getId(), failed);
            return;
        }
        written.stream()
                .filter(message -> !message.fromAddress().equalsIgnoreCase(mailbox.getAddress()))
                .forEach(message -> record(mailbox, thread, message, now));
    }

    private Instant readFromOf(OutreachThread thread) {
        Instant from = messages.findThreadReadFrom(thread.workspaceId(), thread.senderUserId(), thread.threadId());
        if (from == null) {
            return thread.enrollmentId() == null ? clock.instant().minus(properties.outreach().replyListenWindow())
                    : enrollments.findById(thread.enrollmentId()).map(OutreachEnrollment::getEnrolledAt)
                            .orElse(clock.instant());
        }
        return from.minus(READ_OVERLAP);
    }

    /** A message the webhook reported. Its body, where the service sent one, is kept only as the poll's would be. */
    private void received(MailboxConnection mailbox, InboundMessage inbound, Instant now) {
        if (inbound.threadId() == null || inbound.fromAddress() == null
                || inbound.fromAddress().equalsIgnoreCase(mailbox.getAddress())) {
            return;
        }
        Optional<OutreachThread> thread = threadOf(mailbox, inbound.threadId());
        if (thread.isEmpty()) {
            return;
        }
        String text = null;
        if (replyText.readsText(mailbox)) {
            text = inbound.html() != null ? ReplyText.ofBody(inbound.html()) : textReadBack(mailbox, inbound);
        }
        record(mailbox, thread.get(), new ThreadMessage(inbound.messageId(),
                inbound.fromAddress().toLowerCase(Locale.ROOT), inbound.receivedAt(), text), now);
    }

    /** A delivery too large to carry its body names the message; the thread is read for it. */
    private String textReadBack(MailboxConnection mailbox, InboundMessage inbound) {
        if (inbound.messageId() == null) {
            return null;
        }
        Instant since = inbound.receivedAt() == null ? clock.instant().minus(READ_OVERLAP)
                : inbound.receivedAt().minus(READ_OVERLAP);
        try {
            return gateway.messagesInThread(mailbox.getGrantId(), inbound.threadId(), since, true).stream()
                    .filter(message -> inbound.messageId().equals(message.providerMessageId()))
                    .map(ThreadMessage::text)
                    .findFirst().orElse(null);
        } catch (RuntimeException failed) {
            log.warn("Could not read reply {} back at the mail service", inbound.messageId(), failed);
            return null;
        }
    }

    /**
     * One message into one thread, in a transaction of its own. A line already holding its id is left alone; a
     * placeholder of its kind dated after it is completed; anything else is a new line and what it means for the run.
     */
    private void record(MailboxConnection mailbox, OutreachThread thread, ThreadMessage found, Instant now) {
        if (found.fromAddress().length() > MAX_ADDRESS) {
            log.warn("Message {} in thread {} names a sender too long to be an address", found.providerMessageId(),
                    thread.threadId());
            return;
        }
        MessageKind kind = isDeliveryDaemon(found.fromAddress()) ? MessageKind.BOUNCE : MessageKind.REPLY;
        // A mail server's notice can carry a clock a little ahead of the report that told us of it.
        Duration placeholderSlack = kind == MessageKind.BOUNCE ? READ_OVERLAP : Duration.ZERO;
        boolean isNews = found.receivedAt() == null || !found.receivedAt().isBefore(now.minus(ANNOUNCE_WITHIN));
        try {
            transactions.executeWithoutResult(status -> {
                if (found.providerMessageId() != null && messages
                        .existsByWorkspaceIdAndSenderUserIdAndProviderMessageIdAndDirection(thread.workspaceId(),
                                thread.senderUserId(), found.providerMessageId(), MessageDirection.INBOUND)) {
                    return;
                }
                Optional<OutreachMessage> placeholder = messages
                        .findFirstByWorkspaceIdAndSenderUserIdAndThreadIdAndKindAndProviderMessageIdIsNullOrderByOccurredAtAsc(
                                thread.workspaceId(), thread.senderUserId(), thread.threadId(), kind)
                        .filter(waiting -> found.receivedAt() == null
                                || !found.receivedAt().isAfter(waiting.getOccurredAt().plus(placeholderSlack)));
                if (placeholder.isPresent()) {
                    if (found.providerMessageId() != null) {
                        placeholder.get().completeWith(found);
                    }
                    return;
                }
                OutreachMessage line = messages.saveAndFlush(
                        OutreachMessage.received(thread, kind, found, mailbox.getAddress(), now));
                OutreachEnrollment run = thread.enrollmentId() == null ? null
                        : enrollments.findById(thread.enrollmentId()).orElse(null);
                boolean runWaits = run != null && run.isListening();
                if (kind == MessageKind.REPLY && (runWaits || isNews)) {
                    outcomes.replied(line, run, now);
                } else if (kind == MessageKind.BOUNCE && runWaits) {
                    outcomes.bounced(run, now);
                }
            });
        } catch (DataIntegrityViolationException refused) {
            if (isRecordedAlready(refused)) {
                log.debug("Message {} in thread {} was recorded concurrently", found.providerMessageId(),
                        thread.threadId());
            } else {
                log.warn("Message {} in thread {} could not be recorded", found.providerMessageId(), thread.threadId(),
                        refused);
            }
        } catch (OptimisticLockingFailureException raced) {
            // Another writer completed the same placeholder or moved the run meanwhile; the next read settles it.
            log.info("Message {} in thread {} met a concurrent change; the next read retries it",
                    found.providerMessageId(), thread.threadId());
        } catch (RuntimeException failed) {
            // One message must not end the poll for every mailbox after it.
            log.warn("Message {} in thread {} could not be recorded", found.providerMessageId(), thread.threadId(),
                    failed);
        }
    }

    /** The webhook and the poll found the same message at once, and the inbound index kept the other's line. */
    private static boolean isRecordedAlready(DataIntegrityViolationException refused) {
        String cause = String.valueOf(refused.getMostSpecificCause().getMessage());
        return cause.contains("app_lm_outreach_message_inbound_uk");
    }

    private void apply(MailboxEvent event, Instant now) {
        switch (event) {
            case InboundMessage inbound -> throw new IllegalStateException("An inbound message is recorded on its own");
            case DeliveryFailure failure -> mailboxes.findByGrantId(failure.grantId())
                    .forEach(mailbox -> bouncedIn(mailbox, failure, now));
            case MailboxAccessWithdrawn withdrawn -> mailboxes.findByGrantId(withdrawn.grantId())
                    .forEach(MailboxConnection::markAccessWithdrawn);
            case CalendarEventChanged changed -> mailboxes.findByGrantId(changed.grantId())
                    .forEach(mailbox -> meetings.apply(mailbox, changed.event()));
            case CalendarEventRemoved removed -> mailboxes.findByGrantId(removed.grantId())
                    .forEach(mailbox -> meetings.remove(mailbox, removed.eventId()));
            case BookingMade booking -> mailboxes.findByBookingConfigurationId(booking.configurationId())
                    .forEach(mailbox -> linkBookings.apply(mailbox, booking, now));
        }
    }

    /**
     * A bounce the service reported carries no message of its own: it is a placeholder line the mail server's own
     * message completes once a read finds it. A thread whose latest send has already bounced adds nothing, whether
     * the report came twice or the poll found the mail server's message first. Told by when each line was written,
     * not when it says it happened: a placeholder is dated by the report, a completed one by the provider.
     */
    private void bouncedIn(MailboxConnection mailbox, DeliveryFailure failure, Instant now) {
        Optional<OutreachThread> thread = failure.threadId() != null ? threadOf(mailbox, failure.threadId())
                : Optional.ofNullable(failure.messageId())
                        .flatMap(id -> messages.findFirstByWorkspaceIdAndSenderUserIdAndProviderMessageIdAndDirection(
                                mailbox.getWorkspaceId(), mailbox.getUserId(), id, MessageDirection.OUTBOUND))
                        .filter(sent -> sent.getThreadId() != null)
                        .map(OutreachThread::of);
        thread.ifPresent(bounced -> {
            Instant lastSent = messages
                    .findFirstByWorkspaceIdAndSenderUserIdAndThreadIdAndDirectionOrderByCreatedAtDesc(
                            bounced.workspaceId(), bounced.senderUserId(), bounced.threadId(),
                            MessageDirection.OUTBOUND)
                    .map(OutreachMessage::getCreatedAt).orElse(null);
            boolean reported = messages
                    .findFirstByWorkspaceIdAndSenderUserIdAndThreadIdAndKindOrderByCreatedAtDesc(
                            bounced.workspaceId(), bounced.senderUserId(), bounced.threadId(), MessageKind.BOUNCE)
                    .filter(bounce -> lastSent == null || bounce.getCreatedAt().isAfter(lastSent))
                    .isPresent();
            if (!reported) {
                messages.save(OutreachMessage.received(bounced, MessageKind.BOUNCE, null, mailbox.getAddress(), now));
            }
            Optional.ofNullable(bounced.enrollmentId()).flatMap(enrollments::findById)
                    .filter(OutreachEnrollment::isListening)
                    .ifPresent(run -> outcomes.bounced(run, now));
        });
    }

    /**
     * Whose conversation a thread of {@code mailbox} is: its latest send says, and a run whose send went unrecorded
     * says where there is none. Nothing for a thread Uncava never wrote into, or one another gateway made.
     */
    private Optional<OutreachThread> threadOf(MailboxConnection mailbox, String threadId) {
        Optional<OutreachThread> thread = messages
                .findFirstByWorkspaceIdAndSenderUserIdAndThreadIdAndDirectionOrderByOccurredAtDesc(
                        mailbox.getWorkspaceId(), mailbox.getUserId(), threadId, MessageDirection.OUTBOUND)
                .map(OutreachThread::of);
        if (thread.isEmpty()) {
            thread = enrollments.findByWorkspaceIdAndSenderUserIdAndThreadId(mailbox.getWorkspaceId(),
                            mailbox.getUserId(), threadId).stream()
                    .max(Comparator.comparing(OutreachEnrollment::getEnrolledAt))
                    .map(OutreachThread::of);
        }
        return thread.filter(found -> found.isReadableBy(mailbox));
    }

    private static boolean isDeliveryDaemon(String address) {
        int at = address.indexOf('@');
        String local = (at < 0 ? address : address.substring(0, at)).toLowerCase(Locale.ROOT);
        return DELIVERY_DAEMONS.contains(local);
    }

    private record SenderKey(UUID workspaceId, UUID userId) {}

    private record ThreadKey(UUID workspaceId, UUID senderUserId, String threadId) {

        static ThreadKey of(UUID workspaceId, UUID senderUserId, String threadId) {
            return new ThreadKey(workspaceId, senderUserId, threadId);
        }
    }
}
