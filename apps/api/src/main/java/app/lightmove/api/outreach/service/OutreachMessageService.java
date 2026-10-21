package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.candidate.model.RecipientEmail;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.outreach.constant.MessageDirection;
import app.lightmove.api.outreach.constant.MessageKind;
import app.lightmove.api.outreach.constant.MessageStatus;
import app.lightmove.api.outreach.dto.ScheduledMessageResponse;
import app.lightmove.api.outreach.dto.SendMessageRequest;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.OutreachMarkdownEmail;
import app.lightmove.api.outreach.model.OutreachEnrollment;
import app.lightmove.api.outreach.model.OutreachMessage;
import app.lightmove.api.outreach.model.OutreachMessageAttachment;
import app.lightmove.api.outreach.model.StagedAttachment;
import app.lightmove.api.outreach.model.ReplyThreadPreview;
import app.lightmove.api.outreach.model.OutreachThread;
import app.lightmove.api.outreach.model.SenderContext;
import app.lightmove.api.outreach.model.SequenceTokens;
import app.lightmove.api.outreach.model.ThreadReply;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import app.lightmove.api.outreach.repository.OutreachEnrollmentRepository;
import app.lightmove.api.outreach.repository.OutreachMessageAttachmentRepository;
import app.lightmove.api.outreach.repository.OutreachMessageRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * A consultant's own email to one executive: written in the composer, checked as a sequence's first email is at
 * Start, and left as a scheduled line the dispatcher sends — now, or at the time they chose. Never sent here.
 */
@Service
@RequiredArgsConstructor
public class OutreachMessageService {

    /** As far ahead as a sequence may be started. */
    private static final Duration SCHEDULE_HORIZON = Duration.ofDays(60);

    private final OutreachMessageRepository messages;
    private final OutreachMessageAttachmentRepository attachmentRows;
    private final OutreachAttachments attachments;
    private final OutreachEnrollmentRepository enrollments;
    private final MailboxConnectionRepository mailboxes;
    private final CandidateOutreachService people;
    private final OutreachPersonalisation personalisation;
    private final DailySendCount sentToday;
    private final BookingPages bookingPages;
    private final ProjectRepository projects;
    private final TransactionTemplate transactions;
    private final AuditService audit;
    private final Clock clock;

    public ScheduledMessageResponse schedule(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                             SendMessageRequest request, List<MultipartFile> files,
                                             HttpServletRequest httpRequest) {
        projects.requireNotArchived(projectId);
        Instant now = clock.instant();
        Instant sendAt = sendAtOf(request, now);
        if (SequenceTokens.uses(request.body(), SequenceTokens.BOOKING_LINK)) {
            // Before anything is written: a page the mail service will not make is the consultant's to see now.
            bookingPages.prepare(userId, workspaceId);
        }
        List<StagedAttachment> staged = attachments.stage(workspaceId, projectId, candidateId, files,
                request.documentVersionIds());
        OutreachMessage scheduled;
        try {
            scheduled = saveScheduled(userId, workspaceId, projectId, candidateId, request, now, sendAt, staged);
        } catch (RuntimeException refused) {
            attachments.discard(staged);
            throw refused;
        }
        audit.projectEvent(ProjectEventType.OUTREACH_MESSAGE_SCHEDULED, userId, workspaceId, projectId, httpRequest)
                .detail("messageId", scheduled.getId().toString())
                .detail("candidateId", candidateId.toString())
                .detail("inThread", String.valueOf(scheduled.getThreadId() != null))
                .detail("attachments", String.valueOf(staged.size()))
                .record();
        return new ScheduledMessageResponse(scheduled.getId(), scheduled.getStatus(), scheduled.getSendAt(),
                scheduled.getSubject(), scheduled.getThreadId() != null);
    }

    private OutreachMessage saveScheduled(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                          SendMessageRequest request, Instant now, Instant sendAt,
                                          List<StagedAttachment> staged) {
        return transactions.execute(status -> {
            MailboxConnection mailbox = requireSendingMailbox(userId, workspaceId);
            OutreachRecipient recipient = people.currentRecipient(workspaceId, projectId, candidateId)
                    .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
            if (recipient.doNotContact()) {
                throw ApiException.of(ErrorCode.OUTREACH_DO_NOT_CONTACT);
            }
            String toAddress = ledgerSpellingOf(recipient, request.toAddress());
            if (request.sendAt() == null && sentToday.of(mailbox, now) >= mailbox.getDailyCap()) {
                throw ApiException.of(ErrorCode.OUTREACH_DAILY_CAP_REACHED);
            }
            ThreadReply reply = request.replyInThread()
                    ? threadOf(workspaceId, projectId, recipient.personId(), mailbox)
                            .orElseThrow(() -> ApiException.of(ErrorCode.OUTREACH_THREAD_UNAVAILABLE))
                    : null;
            String subject = reply != null ? OutreachSendService.replySubjectOf(reply.subject())
                    : requireSubject(request.subject());
            SenderContext sender = personalisation.senderContextOf(userId, workspaceId, projectId);
            String markdown = OutreachMarkdownEmail.wordsOf(
                    OutreachPersonalisation.tokensOf(recipient, sender, null).renderMarkdown(request.body()));
            OutreachMessage saved = messages.save(OutreachMessage.scheduled(workspaceId, projectId,
                    recipient.personId(), candidateId, userId, mailbox.getAddress(), toAddress, subject,
                    OutreachMarkdownEmail.htmlOf(markdown), markdown, reply, sendAt));
            attachments.record(saved, staged);
            return saved;
        });
    }

    /**
     * What a reply from the caller's mailbox would do, read off the thread {@link #schedule} would answer — empty where it
     * would answer {@code OUTREACH_THREAD_UNAVAILABLE} — and the runs a send there stops, as
     * {@code OutreachMessageSendService} stops them: the caller's own, still live, in that thread.
     */
    public Optional<ReplyThreadPreview> replyPreviewFor(UUID userId, UUID workspaceId, UUID projectId, UUID personId) {
        return mailboxes.findByWorkspaceIdAndUserId(workspaceId, userId)
                .flatMap(mailbox -> threadOf(workspaceId, projectId, personId, mailbox))
                .map(reply -> new ReplyThreadPreview(OutreachSendService.replySubjectOf(reply.subject()),
                        enrollments.findByWorkspaceIdAndSenderUserIdAndThreadId(workspaceId, userId, reply.threadId())
                                .stream().anyMatch(OutreachEnrollment::isLive)));
    }

    /** Only before the dispatcher has taken it: once claimed it may already be on its way. */
    public void cancel(UUID userId, UUID workspaceId, UUID projectId, UUID messageId, HttpServletRequest httpRequest) {
        transactions.executeWithoutResult(status -> {
            OutreachMessage message = messages.findByIdAndWorkspaceIdAndProjectId(messageId, workspaceId, projectId)
                    .filter(found -> found.getKind() == MessageKind.MANUAL)
                    .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
            if (!message.getSenderUserId().equals(userId)) {
                throw ApiException.of(ErrorCode.OUTREACH_MESSAGE_NOT_YOURS);
            }
            if (!message.isScheduled() || message.getSendingSince() != null) {
                throw ApiException.of(ErrorCode.OUTREACH_MESSAGE_NOT_SCHEDULED);
            }
            message.cancel();
            attachments.deleteFilesOnCommit(message.getId());
        });
        audit.projectEvent(ProjectEventType.OUTREACH_MESSAGE_CANCELLED, userId, workspaceId, projectId, httpRequest)
                .detail("messageId", messageId.toString())
                .record();
    }

    /** A file a line of the conversation carried — any line still in it, so never a cancelled one's. */
    public OutreachMessageAttachment attachmentOf(UUID userId, UUID workspaceId, UUID projectId, UUID messageId,
                                                  UUID attachmentId, HttpServletRequest httpRequest) {
        OutreachMessageAttachment file = transactions.execute(status -> {
            messages.findByIdAndWorkspaceIdAndProjectId(messageId, workspaceId, projectId)
                    .filter(message -> message.getStatus() != MessageStatus.CANCELLED)
                    .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
            return attachmentRows.findByIdAndMessageIdAndWorkspaceId(attachmentId, messageId, workspaceId)
                    .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        });
        audit.projectEvent(ProjectEventType.OUTREACH_ATTACHMENT_DOWNLOADED, userId, workspaceId, projectId,
                        httpRequest)
                .detail("messageId", messageId.toString())
                .detail("attachmentId", attachmentId.toString())
                .record();
        return file;
    }

    private Instant sendAtOf(SendMessageRequest request, Instant now) {
        if (request.sendAt() == null) {
            return now;
        }
        if (request.sendAt().isBefore(now)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "sendAt", "Choose a time that is still ahead");
        }
        if (request.sendAt().isAfter(now.plus(SCHEDULE_HORIZON))) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "sendAt", "Choose a time within the next 60 days");
        }
        return request.sendAt();
    }

    /**
     * The thread this mailbox last wrote to the person in on this position, if this mailbox can still write into it,
     * and the message in it a reply answers: their latest, else ours.
     */
    private Optional<ThreadReply> threadOf(UUID workspaceId, UUID projectId, UUID personId,
                                          MailboxConnection mailbox) {
        Optional<OutreachMessage> lastSent = messages
                .findFirstByWorkspaceIdAndProjectIdAndPersonIdAndSenderUserIdAndDirectionAndStatusAndThreadIdIsNotNullOrderByOccurredAtDesc(
                        workspaceId, projectId, personId, mailbox.getUserId(), MessageDirection.OUTBOUND,
                        MessageStatus.SENT)
                .filter(sent -> OutreachThread.of(sent).isReadableBy(mailbox));
        return lastSent.flatMap(sent -> {
            String threadId = sent.getThreadId();
            String subject = messages
                    .findFirstByWorkspaceIdAndSenderUserIdAndThreadIdAndDirectionAndSubjectIsNotNullOrderByOccurredAtAsc(
                            workspaceId, mailbox.getUserId(), threadId, MessageDirection.OUTBOUND)
                    .map(OutreachMessage::getSubject)
                    .orElse(sent.getSubject());
            return messages
                    .findFirstByWorkspaceIdAndSenderUserIdAndThreadIdAndProviderMessageIdIsNotNullAndKindNotOrderByOccurredAtDesc(
                            workspaceId, mailbox.getUserId(), threadId, MessageKind.BOUNCE)
                    .map(answered -> new ThreadReply(threadId, sent.getThreadGateway(),
                            answered.getProviderMessageId(), subject));
        });
    }

    /** One line, as an email header must be: a line break would be refused by the mail service anyway. */
    private static String requireSubject(String subject) {
        String trimmed = subject == null ? "" : subject.strip();
        if (trimmed.isEmpty()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "subject", "Give the email a subject");
        }
        if (trimmed.contains("\n") || trimmed.contains("\r")) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "subject", "Keep the subject to one line");
        }
        return trimmed;
    }

    private MailboxConnection requireSendingMailbox(UUID userId, UUID workspaceId) {
        MailboxConnection mailbox = mailboxes.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.MAILBOX_NOT_CONNECTED));
        if (!mailbox.canSend()) {
            throw ApiException.of(ErrorCode.MAILBOX_RECONNECT_NEEDED);
        }
        return mailbox;
    }

    private static String ledgerSpellingOf(OutreachRecipient recipient, String requested) {
        Optional<String> held = recipient.emails().stream()
                .map(RecipientEmail::address)
                .filter(address -> address.equalsIgnoreCase(requested.strip()))
                .findFirst();
        return held.orElseThrow(() -> ApiException.of(ErrorCode.OUTREACH_ADDRESS_NOT_ON_FILE));
    }
}
