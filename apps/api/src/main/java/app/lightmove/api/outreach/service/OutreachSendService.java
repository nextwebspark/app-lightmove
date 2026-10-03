package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.OutreachSettings;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.outreach.constant.EnrollmentStatus;
import app.lightmove.api.outreach.constant.OutreachStopReason;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.OutgoingEmail;
import app.lightmove.api.outreach.model.OutreachEmailBody;
import app.lightmove.api.outreach.model.OutreachEnrollment;
import app.lightmove.api.outreach.model.OutreachMessage;
import app.lightmove.api.outreach.model.OutreachSequence;
import app.lightmove.api.outreach.model.PreparedSend;
import app.lightmove.api.outreach.model.SendingWindow;
import app.lightmove.api.outreach.model.SentEmail;
import app.lightmove.api.outreach.model.SequenceStep;
import app.lightmove.api.outreach.model.SequenceTokens;
import app.lightmove.api.outreach.model.SenderContext;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import app.lightmove.api.outreach.repository.OutreachEnrollmentClaims;
import app.lightmove.api.outreach.repository.OutreachEnrollmentRepository;
import app.lightmove.api.outreach.repository.OutreachMessageRepository;
import app.lightmove.api.outreach.repository.OutreachSequenceRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends one claimed enrollment's next email: re-checks the person and the mailbox, holds the email to
 * the sender's window and daily cap, sends it, and records it. Three short transactions with the mail
 * service called between them, never inside one — and never twice: a send that failed, or whose outcome
 * is unknown, stops the run rather than trying again.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutreachSendService {

    private static final String REPLY_PREFIX = "Re: ";

    private static final Duration RATE_LIMITED_PAUSE = Duration.ofMinutes(1);
    private static final Duration APP_UNAVAILABLE_PAUSE = Duration.ofHours(1);

    private final BookingPages bookingPages;
    private final OutreachEnrollmentClaims claims;
    private final OutreachEnrollmentRepository enrollments;
    private final OutreachSequenceRepository sequences;
    private final OutreachMessageRepository messages;
    private final MailboxConnectionRepository mailboxes;
    private final CandidateOutreachService people;
    private final OutreachPersonalisation personalisation;
    private final OutreachOutcomes outcomes;
    private final MailboxGateway gateway;
    private final TransactionTemplate transactions;
    private final AuditService audit;
    private final LightMoveProperties properties;

    public List<UUID> claimDue(Instant now) {
        return claims.claimDue(now, settings().dispatchBatch());
    }

    /**
     * A claim that outlived its dispatcher is a send nobody knows the fate of — the instance may have died
     * before the mail service answered, or after. Stopped, never resent.
     */
    public void stopUncertainClaims(Instant now) {
        transactions.executeWithoutResult(status -> enrollments
                .findByStatusInAndSendingSinceBefore(EnrollmentStatus.LIVE, now.minus(settings().claimTimeout()))
                .forEach(enrollment -> {
                    log.warn("Outreach enrollment {} was claimed at {} and never released; stopping it unsent",
                            enrollment.getId(), enrollment.getSendingSince());
                    outcomes.stop(enrollment, OutreachStopReason.SEND_UNCERTAIN, null, now, null);
                }));
    }

    public void sendClaimed(UUID enrollmentId, Instant now) {
        PreparedSend prepared;
        try {
            prepared = transactions.execute(status -> prepare(enrollmentId, now));
        } catch (RuntimeException failed) {
            // Nothing was sent, so the claim is released and the next dispatch looks again.
            transactions.executeWithoutResult(status -> enrollments.findById(enrollmentId)
                    .ifPresent(enrollment -> enrollment.deferTo(enrollment.getNextSendAt())));
            throw failed;
        }
        if (prepared == null) {
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

    /**
     * The email went, so whatever fails from here it is never sent again. A Stop pressed while it was in
     * flight loses the version race once; the second attempt reads the stopped row, which markSent keeps.
     */
    private void recordDelivered(PreparedSend prepared, SentEmail sent, Instant now) {
        try {
            try {
                transactions.executeWithoutResult(status -> recordSent(prepared, sent, now));
            } catch (OptimisticLockingFailureException raced) {
                transactions.executeWithoutResult(status -> recordSent(prepared, sent, now));
            }
        } catch (RuntimeException failed) {
            log.error("Outreach email for enrollment {} was sent but could not be recorded; keeping its thread "
                    + "so a reply is still heard", prepared.enrollmentId(), failed);
            claims.recordDelivered(prepared.enrollmentId(), sent, now);
        }
    }

    private PreparedSend prepare(UUID enrollmentId, Instant now) {
        OutreachEnrollment enrollment = enrollments.findById(enrollmentId).orElse(null);
        if (enrollment == null || enrollment.getSendingSince() == null) {
            return null;
        }
        if (!enrollment.isLive()) {
            // Answered or stopped while it waited in the batch.
            enrollment.releaseClaim();
            return null;
        }
        MailboxConnection mailbox = mailboxes
                .findByWorkspaceIdAndUserId(enrollment.getWorkspaceId(), enrollment.getSenderUserId())
                .filter(MailboxConnection::canSend)
                .orElse(null);
        if (mailbox == null) {
            outcomes.stop(enrollment, OutreachStopReason.MAILBOX_INACTIVE, null, now, null);
            return null;
        }
        Optional<OutreachRecipient> recipient = enrollment.getCandidateId() == null ? Optional.empty()
                : people.currentRecipient(enrollment.getWorkspaceId(), enrollment.getProjectId(),
                        enrollment.getCandidateId());
        OutreachStopReason refused = recipient.isEmpty() ? OutreachStopReason.UNMAPPED
                : refusalOf(recipient.get(), enrollment);
        if (refused != null) {
            outcomes.stop(enrollment, refused, null, now, null);
            return null;
        }
        OutreachSequence sequence = sequences.findById(enrollment.getSequenceId()).orElseThrow();
        if (enrollment.getNextStep() >= sequence.getSteps().size()) {
            enrollment.complete();
            return null;
        }

        SendingWindow window = SendingWindow.of(settings());
        ZoneId zone = mailbox.zone();
        if (!window.isOpen(now, zone)) {
            enrollment.deferTo(window.nextOpening(now, zone));
            return null;
        }
        // A soft cap: one instance counts every send it made, since each commits before the next prepare,
        // but instances running at once can each pass at the same count and overshoot by one email apiece.
        long sentToday = messages.countByWorkspaceIdAndSenderUserIdAndSentAtGreaterThanEqual(
                enrollment.getWorkspaceId(), enrollment.getSenderUserId(), window.startOfDay(now, zone));
        if (sentToday >= mailbox.getDailyCap()) {
            enrollment.deferTo(window.openingAfterToday(now, zone));
            return null;
        }
        return new PreparedSend(enrollment.getId(), mailbox.getGrantId(),
                emailOf(enrollment, sequence, recipient.orElseThrow(), mailbox), enrollment.getNextStep());
    }

    /** Everything Add to sequence refused at Start, asked again: any of it may have changed since. */
    private static OutreachStopReason refusalOf(OutreachRecipient person, OutreachEnrollment enrollment) {
        if (person.doNotContact()) {
            return OutreachStopReason.DO_NOT_CONTACT;
        }
        if (person.status().hasLeftTheRunning()) {
            return OutreachStopReason.LEFT_THE_RUNNING;
        }
        if (!person.holdsEmail(enrollment.getToAddress())) {
            return OutreachStopReason.ADDRESS_REMOVED;
        }
        return null;
    }

    /**
     * The first email is the one the consultant reviewed, frozen at Start. A follow-up is rendered from
     * the sequence as it stands now and goes as a reply to the last email, which keeps it in the thread.
     */
    private OutgoingEmail emailOf(OutreachEnrollment enrollment, OutreachSequence sequence,
                                  OutreachRecipient recipient, MailboxConnection mailbox) {
        int step = enrollment.getNextStep();
        if (step == 0) {
            return new OutgoingEmail(enrollment.getToAddress(), enrollment.getFirstSubject(),
                    OutreachEmailBody.htmlOf(enrollment.getFirstBody(), bookingPages.linkOf(mailbox)));
        }
        SequenceStep followUp = sequence.getSteps().get(step);
        SenderContext sender = personalisation.senderContextOf(enrollment.getSenderUserId(),
                enrollment.getWorkspaceId(), enrollment.getProjectId());
        // A follow-up edited to carry the link after Start: the slug is all the email needs, the page comes later.
        if (sender.bookingLink() == null && SequenceTokens.uses(followUp.getBody(), SequenceTokens.BOOKING_LINK)) {
            sender = new SenderContext(sender.positionTitle(), sender.senderFirstName(),
                    bookingPages.claimLink(mailbox));
        }
        String body = OutreachPersonalisation.tokensOf(recipient, sender, enrollment.getOpener())
                .render(followUp.getBody());
        return new OutgoingEmail(enrollment.getToAddress(), replySubjectOf(enrollment.getFirstSubject()),
                OutreachEmailBody.htmlOf(body, sender.bookingLink()), enrollment.getLastMessageId());
    }

    private void recordSent(PreparedSend prepared, SentEmail sent, Instant now) {
        OutreachEnrollment enrollment = enrollments.findById(prepared.enrollmentId()).orElseThrow();
        OutreachSequence sequence = sequences.findById(enrollment.getSequenceId()).orElseThrow();
        int following = prepared.step() + 1;
        Instant followingDue = following < sequence.getSteps().size()
                ? SendingWindow.of(settings()).addWorkingDays(now, zoneOf(enrollment),
                        sequence.getSteps().get(following).getDelayWorkingDays())
                : null;
        enrollment.markSent(sent, now, followingDue);
        messages.save(OutreachMessage.sent(enrollment, prepared.step(), prepared.email(), sent, now));
        if (enrollment.getCandidateId() != null) {
            people.recordEmailSent(enrollment.getSenderUserId(), enrollment.getProjectId(), enrollment.getCandidateId(),
                    sequence.getId(), sequence.getName(), prepared.step() + 1);
        }
        audit.projectEvent(ProjectEventType.OUTREACH_EMAIL_SENT, enrollment.getSenderUserId(),
                        enrollment.getWorkspaceId(), enrollment.getProjectId(), null)
                .detail("sequenceId", sequence.getId().toString())
                .detail("enrollmentId", enrollment.getId().toString())
                .detail("step", prepared.step() + 1)
                .record();
    }

    private void recordFailure(PreparedSend prepared, RuntimeException failed, Instant now) {
        OutreachEnrollment enrollment = enrollments.findById(prepared.enrollmentId()).orElseThrow();
        if (!enrollment.isLive()) {
            enrollment.releaseClaim();
            return;
        }
        if (failed instanceof ProviderAppUnavailable) {
            // The workspace's OAuth app failed before anything left; the run waits for an admin to fix it.
            log.warn("Outreach send for enrollment {} waits: its workspace's mail app is unavailable",
                    enrollment.getId());
            enrollment.deferTo(now.plus(APP_UNAVAILABLE_PAUSE));
            return;
        }
        VendorFailureKind kind = failed instanceof VendorException vendor ? vendor.getKind() : null;
        if (kind == VendorFailureKind.RATE_LIMITED) {
            // Refused before it left — our own permit, or the service's 429 — so it is simply not yet.
            log.info("Outreach send for enrollment {} was rate limited; trying again shortly", enrollment.getId());
            enrollment.deferTo(now.plus(RATE_LIMITED_PAUSE));
            return;
        }
        OutreachStopReason reason = stopReasonOf(failed, kind);
        if (reason == OutreachStopReason.MAILBOX_INACTIVE) {
            mailboxes.findByWorkspaceIdAndUserId(enrollment.getWorkspaceId(), enrollment.getSenderUserId())
                    .ifPresent(MailboxConnection::markAccessWithdrawn);
        }
        log.warn("Outreach send for enrollment {} failed; stopping it as {}", enrollment.getId(), reason, failed);
        outcomes.stop(enrollment, reason, null, now, null);
    }

    /** A timeout or a 5xx may still have been queued, so it is uncertain; anything else was plainly refused. */
    private static OutreachStopReason stopReasonOf(RuntimeException failed, VendorFailureKind kind) {
        if (failed instanceof VendorException vendor && MailboxService.isAccessWithdrawn(vendor)) {
            return OutreachStopReason.MAILBOX_INACTIVE;
        }
        if (kind == VendorFailureKind.TIMEOUT || kind == VendorFailureKind.UNAVAILABLE) {
            return OutreachStopReason.SEND_UNCERTAIN;
        }
        return OutreachStopReason.SEND_FAILED;
    }

    private ZoneId zoneOf(OutreachEnrollment enrollment) {
        return mailboxes.findByWorkspaceIdAndUserId(enrollment.getWorkspaceId(), enrollment.getSenderUserId())
                .map(MailboxConnection::zone)
                .orElse(MailboxConnection.DEFAULT_ZONE);
    }

    static String replySubjectOf(String firstSubject) {
        return firstSubject.regionMatches(true, 0, REPLY_PREFIX, 0, REPLY_PREFIX.length())
                ? firstSubject : REPLY_PREFIX + firstSubject;
    }

    private OutreachSettings settings() {
        return properties.outreach();
    }
}
