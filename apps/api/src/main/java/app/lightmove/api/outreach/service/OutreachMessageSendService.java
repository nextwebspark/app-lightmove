package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.OutreachSettings;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.outreach.constant.MailboxGatewayKind;
import app.lightmove.api.outreach.constant.MessageStatus;
import app.lightmove.api.outreach.constant.OutreachStopReason;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.OutgoingEmail;
import app.lightmove.api.outreach.model.OutreachEmailSentByHand;
import app.lightmove.api.outreach.model.OutreachEnrollment;
import app.lightmove.api.outreach.model.OutreachMessage;
import app.lightmove.api.outreach.model.OutreachMessageAttachment;
import app.lightmove.api.outreach.model.PreparedOwnSend;
import app.lightmove.api.outreach.model.SentEmail;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import app.lightmove.api.outreach.repository.OutreachEnrollmentRepository;
import app.lightmove.api.outreach.repository.OutreachMessageClaims;
import app.lightmove.api.outreach.repository.OutreachMessageRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends a consultant's own claimed email: re-checks the person, the address and the mailbox, holds it to the daily
 * cap — never to a sequence's window, since a person chose its time — sends it and records it. Three short
 * transactions with the mail service called between them, as {@link OutreachSendService}'s are, and never twice: a
 * send that failed, or whose outcome is unknown, is a failed line rather than another try.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutreachMessageSendService {

    private static final Duration RATE_LIMITED_PAUSE = Duration.ofMinutes(1);
    private static final Duration APP_UNAVAILABLE_PAUSE = Duration.ofHours(1);

    private final OutreachMessageClaims claims;
    private final OutreachMessageRepository messages;
    private final OutreachAttachments attachments;
    private final DailySendCount sentToday;
    private final OutreachEnrollmentRepository enrollments;
    private final MailboxConnectionRepository mailboxes;
    private final CandidateOutreachService people;
    private final OutreachOutcomes outcomes;
    private final ProjectRepository projects;
    private final ProjectAccess access;
    private final MailboxGateway gateway;
    private final TransactionTemplate transactions;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final LightMoveProperties properties;

    public List<UUID> claimDue(Instant now) {
        return claims.claimDue(now, settings().dispatchBatch());
    }

    /** A claim that outlived its dispatcher is an email nobody knows the fate of. Failed, never resent. */
    public void failUncertainClaims(Instant now) {
        transactions.executeWithoutResult(status -> messages
                .findByStatusAndSendingSinceBefore(MessageStatus.SCHEDULED, now.minus(settings().claimTimeout()))
                .forEach(message -> {
                    log.warn("Outreach message {} was claimed at {} and never released; failing it unsent",
                            message.getId(), message.getSendingSince());
                    failed(message, OutreachStopReason.SEND_UNCERTAIN, now);
                }));
    }

    public void sendClaimed(UUID messageId, Instant now) {
        PreparedOwnSend checked;
        try {
            checked = transactions.execute(status -> prepare(messageId, now));
        } catch (RuntimeException failed) {
            // Nothing was sent, so the claim is released and the next dispatch looks again.
            transactions.executeWithoutResult(status -> messages.findById(messageId)
                    .filter(OutreachMessage::isScheduled)
                    .ifPresent(OutreachMessage::releaseClaim));
            throw failed;
        }
        if (checked == null) {
            return;
        }
        PreparedOwnSend prepared;
        try {
            prepared = checked.carrying(attachments.contentOf(messageId));
        } catch (RuntimeException unreadable) {
            // Nothing went: a file it was to carry cannot be read, and it never goes without it.
            transactions.executeWithoutResult(status -> recordFailure(checked, unreadable, now));
            return;
        }

        SentEmail sent;
        try {
            sent = gateway.send(prepared.grantId(), prepared.email());
        } catch (RuntimeException failed) {
            transactions.executeWithoutResult(status -> recordFailure(prepared, failed, now));
            return;
        }
        recordDelivered(prepared, sent, now);
    }

    private void recordDelivered(PreparedOwnSend prepared, SentEmail sent, Instant now) {
        try {
            try {
                transactions.executeWithoutResult(status -> recordSent(prepared, sent, now));
            } catch (OptimisticLockingFailureException raced) {
                transactions.executeWithoutResult(status -> recordSent(prepared, sent, now));
            }
        } catch (RuntimeException failed) {
            log.error("Outreach message {} was sent but could not be recorded; marking it sent so it never goes "
                    + "again", prepared.messageId(), failed);
            claims.recordDelivered(prepared.messageId(), sent, MailboxGatewayKind.ofGrant(prepared.grantId()), now);
        }
    }

    private PreparedOwnSend prepare(UUID messageId, Instant now) {
        OutreachMessage message = messages.findById(messageId).orElse(null);
        if (message == null || !message.isScheduled() || message.getSendingSince() == null) {
            return null;
        }
        if (projects.existsByIdAndArchivedAtIsNotNull(message.getProjectId())) {
            failed(message, OutreachStopReason.POSITION_ARCHIVED, now);
            return null;
        }
        MailboxConnection mailbox = mailboxes
                .findByWorkspaceIdAndUserId(message.getWorkspaceId(), message.getSenderUserId())
                .filter(MailboxConnection::canSend)
                .orElse(null);
        if (mailbox == null) {
            failed(message, OutreachStopReason.MAILBOX_INACTIVE, now);
            return null;
        }
        if (message.getThreadId() != null && (message.getThreadGateway() != mailbox.getGateway()
                || !mailbox.getAddress().equalsIgnoreCase(message.getFromAddress()))) {
            // The thread it answers was written from another gateway or address, which this mailbox cannot write
            // into, nor read a reply from.
            failed(message, OutreachStopReason.MAILBOX_MOVED, now);
            return null;
        }
        if (!access.allows(message.getSenderUserId(), message.getWorkspaceId(), message.getProjectId(),
                ProjectAction.WORK_EXECUTE)) {
            failed(message, OutreachStopReason.SENDER_WITHOUT_ACCESS, now);
            return null;
        }
        Optional<OutreachRecipient> recipient = message.getCandidateId() == null ? Optional.empty()
                : people.currentRecipient(message.getWorkspaceId(), message.getProjectId(), message.getCandidateId());
        OutreachStopReason refused = recipient.isEmpty() ? OutreachStopReason.UNMAPPED
                : refusalOf(recipient.get(), message);
        if (refused != null) {
            failed(message, refused, now);
            return null;
        }
        ZoneId zone = mailbox.zone();
        if (sentToday.of(mailbox, now) >= mailbox.getDailyCap()) {
            message.deferTo(LocalDate.ofInstant(now, zone).plusDays(1).atTime(settings().windowStart()).atZone(zone)
                    .toInstant());
            return null;
        }
        message.sendFrom(mailbox.getAddress());
        return new PreparedOwnSend(message.getId(), mailbox.getGrantId(), new OutgoingEmail(message.getToAddress(),
                message.getSubject(), message.getBody(), message.getInReplyTo()));
    }

    /** What the composer refused, asked again: any of it may have changed while the email waited. */
    private static OutreachStopReason refusalOf(OutreachRecipient person, OutreachMessage message) {
        if (person.doNotContact()) {
            return OutreachStopReason.DO_NOT_CONTACT;
        }
        if (!person.holdsEmail(message.getToAddress())) {
            return OutreachStopReason.ADDRESS_REMOVED;
        }
        return null;
    }

    /**
     * A person who writes into a thread a sequence is still running in has taken it over: the run stops, so no canned
     * follow-up lands after their own words.
     */
    private void recordSent(PreparedOwnSend prepared, SentEmail sent, Instant now) {
        OutreachMessage message = messages.findById(prepared.messageId()).orElseThrow();
        message.markSent(sent, MailboxGatewayKind.ofGrant(prepared.grantId()), now);
        if (message.getCandidateId() != null) {
            people.recordOwnEmailSent(message.getSenderUserId(), message.getProjectId(), message.getCandidateId(),
                    message.getId());
        }
        enrollments.findByWorkspaceIdAndSenderUserIdAndThreadId(message.getWorkspaceId(), message.getSenderUserId(),
                        message.getThreadId()).stream()
                .filter(OutreachEnrollment::isLive)
                .forEach(run -> outcomes.stop(run, OutreachStopReason.MANUAL, message.getSenderUserId(), now, null));
        audit.projectEvent(ProjectEventType.OUTREACH_MESSAGE_SENT, message.getSenderUserId(),
                        message.getWorkspaceId(), message.getProjectId(), null)
                .detail("messageId", message.getId().toString())
                .detailIfPresent("candidateId", message.getCandidateId() == null ? null
                        : message.getCandidateId().toString())
                .record();
        auditDocumentsSent(message);
        events.publishEvent(new OutreachEmailSentByHand(message.getWorkspaceId(), message.getProjectId(),
                message.getPersonId(), message.getCandidateId(), message.getSenderUserId(), message.getId()));
    }

    /** A person's document that went with the email left the app as a download does, and is recorded as one. */
    private void auditDocumentsSent(OutreachMessage message) {
        attachments.of(message.getId()).stream()
                .filter(OutreachMessageAttachment::isPersonDocument)
                .forEach(attachment -> audit.event(ProjectEventType.PERSON_DOCUMENT_SENT)
                        .actor(message.getSenderUserId())
                        .workspace(message.getWorkspaceId())
                        .target("person", message.getPersonId())
                        .detail("messageId", message.getId().toString())
                        .detail("projectId", message.getProjectId().toString())
                        .detailIfPresent("versionId", attachment.getDocumentVersionId() == null ? null
                                : attachment.getDocumentVersionId().toString())
                        .detail("fileName", attachment.getFileName())
                        .record());
    }

    private void recordFailure(PreparedOwnSend prepared, RuntimeException failed, Instant now) {
        OutreachMessage message = messages.findById(prepared.messageId()).orElseThrow();
        if (!message.isScheduled()) {
            return;
        }
        if (failed instanceof ProviderAppUnavailable) {
            log.warn("Outreach message {} waits: its workspace's mail app is unavailable", message.getId());
            message.deferTo(now.plus(APP_UNAVAILABLE_PAUSE));
            return;
        }
        VendorFailureKind kind = failed instanceof VendorException vendor ? vendor.getKind() : null;
        if (kind == VendorFailureKind.RATE_LIMITED) {
            log.info("Outreach message {} was rate limited; trying again shortly", message.getId());
            message.deferTo(now.plus(RATE_LIMITED_PAUSE));
            return;
        }
        OutreachStopReason reason;
        if (failed instanceof VendorException vendor && MailboxService.isAccessWithdrawn(vendor)) {
            reason = OutreachStopReason.MAILBOX_INACTIVE;
            mailboxes.findByWorkspaceIdAndUserId(message.getWorkspaceId(), message.getSenderUserId())
                    .ifPresent(MailboxConnection::markAccessWithdrawn);
        } else if (kind == VendorFailureKind.TIMEOUT || kind == VendorFailureKind.UNAVAILABLE) {
            reason = OutreachStopReason.SEND_UNCERTAIN;
        } else {
            reason = OutreachStopReason.SEND_FAILED;
        }
        log.warn("Outreach message {} failed to send as {}", message.getId(), reason, failed);
        failed(message, reason, now);
    }

    private void failed(OutreachMessage message, OutreachStopReason reason, Instant now) {
        message.fail(reason, now);
        audit.projectEvent(ProjectEventType.OUTREACH_MESSAGE_FAILED, message.getSenderUserId(),
                        message.getWorkspaceId(), message.getProjectId(), null)
                .detail("messageId", message.getId().toString())
                .detail("reason", reason.name())
                .record();
    }

    private OutreachSettings settings() {
        return properties.outreach();
    }
}
