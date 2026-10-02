package app.lightmove.api.outreach.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.outreach.constant.EnrollmentStatus;
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
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import app.lightmove.api.outreach.repository.OutreachEnrollmentRepository;
import app.lightmove.api.outreach.repository.OutreachMessageRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What comes back into a sender's mailbox: a reply stops the person's run, a bounce ends it, a
 * mailbox whose access was withdrawn stops sending, a calendar event with a mapped person is kept as
 * their meeting, and a booking through a consultant's link ends that person's run. Learned from the mail service's webhook, and from a
 * poll for whatever a webhook never delivered. Only who wrote and where is ever read — never what.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutreachInboxService {

    /** Who answers for a mail server that could not deliver: their message in the thread is a bounce, not a reply. */
    private static final Set<String> DELIVERY_DAEMONS = Set.of("mailer-daemon", "postmaster");

    private final MailboxGateway gateway;
    private final MailboxConnectionRepository mailboxes;
    private final OutreachEnrollmentRepository enrollments;
    private final OutreachMessageRepository messages;
    private final OutreachOutcomes outcomes;
    private final MeetingSync meetings;
    private final LinkBookings linkBookings;
    private final TransactionTemplate transactions;
    private final LightMoveProperties properties;
    private final Clock clock;

    /** One webhook delivery; refused by the gateway when its signature does not verify. */
    public void receive(String signature, byte[] body) {
        Instant now = clock.instant();
        for (MailboxEvent event : gateway.readWebhook(signature, body)) {
            transactions.executeWithoutResult(status -> apply(event, now));
        }
    }

    /** The fallback for a missed webhook: every thread still listening, asked who has written into it. */
    public void pollListeningThreads(Instant now) {
        Instant since = now.minus(properties.outreach().replyListenWindow());
        Map<SenderKey, List<OutreachEnrollment>> bySender = enrollments
                .findListeningSince(since, Set.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED)).stream()
                .collect(Collectors.groupingBy(enrollment -> new SenderKey(enrollment.getWorkspaceId(),
                        enrollment.getSenderUserId())));
        bySender.forEach((sender, listening) -> mailboxes
                .findByWorkspaceIdAndUserId(sender.workspaceId(), sender.userId())
                .filter(MailboxConnection::canSend)
                .ifPresent(mailbox -> listening.forEach(enrollment -> pollThread(mailbox, enrollment, now))));
    }

    private void pollThread(MailboxConnection mailbox, OutreachEnrollment enrollment, Instant now) {
        List<String> writers;
        try {
            writers = gateway.senderAddressesInThread(mailbox.getGrantId(), enrollment.getThreadId(),
                    enrollment.getEnrolledAt());
        } catch (RuntimeException failed) {
            log.warn("Could not read outreach thread of enrollment {} at the mail service", enrollment.getId(), failed);
            return;
        }
        writers.stream()
                .filter(address -> !address.equalsIgnoreCase(mailbox.getAddress()))
                .findFirst()
                .ifPresent(address -> transactions.executeWithoutResult(status -> enrollments
                        .findById(enrollment.getId())
                        .filter(OutreachEnrollment::isListening)
                        .ifPresent(fresh -> answered(fresh, address, now))));
    }

    private void apply(MailboxEvent event, Instant now) {
        switch (event) {
            case InboundMessage inbound -> mailboxes.findByGrantId(inbound.grantId()).stream()
                    .filter(mailbox -> inbound.threadId() != null && inbound.fromAddress() != null)
                    .filter(mailbox -> !inbound.fromAddress().equalsIgnoreCase(mailbox.getAddress()))
                    .flatMap(mailbox -> listeningIn(mailbox, inbound.threadId()).stream())
                    .forEach(enrollment -> answered(enrollment, inbound.fromAddress(), now));
            case DeliveryFailure failure -> mailboxes.findByGrantId(failure.grantId())
                    .forEach(mailbox -> bouncedIn(mailbox, failure).filter(OutreachEnrollment::isListening)
                            .ifPresent(enrollment -> outcomes.bounced(enrollment, now)));
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

    private void answered(OutreachEnrollment enrollment, String fromAddress, Instant now) {
        if (isDeliveryDaemon(fromAddress)) {
            outcomes.bounced(enrollment, now);
        } else {
            outcomes.replied(enrollment, now);
        }
    }

    private List<OutreachEnrollment> listeningIn(MailboxConnection mailbox, String threadId) {
        return enrollments.findByWorkspaceIdAndSenderUserIdAndThreadId(mailbox.getWorkspaceId(), mailbox.getUserId(),
                        threadId).stream()
                .filter(OutreachEnrollment::isListening)
                .toList();
    }

    private Optional<OutreachEnrollment> bouncedIn(MailboxConnection mailbox, DeliveryFailure failure) {
        if (failure.threadId() != null) {
            return listeningIn(mailbox, failure.threadId()).stream().findFirst();
        }
        if (failure.messageId() == null) {
            return Optional.empty();
        }
        return messages.findFirstByWorkspaceIdAndSenderUserIdAndProviderMessageId(mailbox.getWorkspaceId(),
                        mailbox.getUserId(), failure.messageId())
                .map(OutreachMessage::getEnrollmentId)
                .flatMap(enrollments::findById);
    }

    private static boolean isDeliveryDaemon(String address) {
        int at = address.indexOf('@');
        String local = (at < 0 ? address : address.substring(0, at)).toLowerCase(Locale.ROOT);
        return DELIVERY_DAEMONS.contains(local);
    }

    private record SenderKey(UUID workspaceId, UUID userId) {}
}
