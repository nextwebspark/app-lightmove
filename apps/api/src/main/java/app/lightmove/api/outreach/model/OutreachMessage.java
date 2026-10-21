package app.lightmove.api.outreach.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.outreach.constant.MailboxGatewayKind;
import app.lightmove.api.outreach.constant.MessageBodyFormat;
import app.lightmove.api.outreach.constant.MessageChannel;
import app.lightmove.api.outreach.constant.MessageDirection;
import app.lightmove.api.outreach.constant.MessageKind;
import app.lightmove.api.outreach.constant.MessageStatus;
import app.lightmove.api.outreach.constant.OutreachStopReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One line of an executive's conversation on a position (V101, widened by V144–V146): a sequence's email once it
 * went, a consultant's own email from scheduled to sent, or a reply or bounce in its thread. Staff-only; nobody's
 * words are ever logged.
 */
@Entity
@Table(name = "app_lm_outreach_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutreachMessage extends BaseEntity {

    /** A line's text is held to this, so one long email cannot crowd out a whole conversation's read. */
    public static final int MAX_TEXT = 20_000;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Column(name = "person_id", updatable = false)
    private UUID personId;

    /** Null once the person is taken off the position; the line stays theirs by {@code personId}. */
    @Column(name = "candidate_id", updatable = false)
    private UUID candidateId;

    @Column(name = "enrollment_id", updatable = false)
    private UUID enrollmentId;

    /** Zero-based, as the sequence numbers its steps; null on a line no sequence sent. */
    @Column(name = "step", updatable = false)
    private Integer step;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, updatable = false)
    private MessageDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, updatable = false)
    private MessageChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false)
    private MessageKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MessageStatus status;

    @Column(name = "sender_user_id", nullable = false, updatable = false)
    private UUID senderUserId;

    @Column(name = "from_address")
    private String fromAddress;

    @Column(name = "to_address")
    private String toAddress;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "thread_id")
    private String threadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "thread_gateway")
    private MailboxGatewayKind threadGateway;

    @Column(name = "subject", updatable = false)
    private String subject;

    /** The HTML as it went; the screens read {@code bodyText}, never this. */
    @Column(name = "body", updatable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_format", nullable = false, updatable = false)
    private MessageBodyFormat bodyFormat;

    @Column(name = "body_text")
    private String bodyText;

    /** Set only on an outbound email that went: the daily cap counts these. */
    @Column(name = "sent_at")
    private Instant sentAt;

    /** When a scheduled line is due; it never moves for a window, only for the daily cap. */
    @Column(name = "send_at")
    private Instant sendAt;

    /** The dispatcher's claim, written by {@code OutreachMessageClaims} and released here. */
    @Column(name = "sending_since")
    private Instant sendingSince;

    /** The provider's id of the message this one answers. */
    @Column(name = "in_reply_to", updatable = false)
    private String inReplyTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "fail_reason")
    private OutreachStopReason failReason;

    /** Where the line sits in the conversation. */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** {@code text} is the plain text the HTML was rendered from, booking link and all. */
    public static OutreachMessage sent(OutreachEnrollment enrollment, int step, OutgoingEmail email, String text,
                                       String fromAddress, SentEmail sent, MailboxGatewayKind gateway, Instant now) {
        OutreachMessage message = new OutreachMessage();
        message.workspaceId = enrollment.getWorkspaceId();
        message.projectId = enrollment.getProjectId();
        message.personId = enrollment.getPersonId();
        message.candidateId = enrollment.getCandidateId();
        message.enrollmentId = enrollment.getId();
        message.step = step;
        message.direction = MessageDirection.OUTBOUND;
        message.channel = MessageChannel.EMAIL;
        message.kind = MessageKind.SEQUENCE_STEP;
        message.status = MessageStatus.SENT;
        message.senderUserId = enrollment.getSenderUserId();
        message.fromAddress = fromAddress;
        message.toAddress = email.to();
        message.providerMessageId = sent.messageId();
        message.threadId = sent.threadId();
        message.threadGateway = gateway;
        message.subject = email.subject();
        message.body = email.htmlBody();
        message.bodyFormat = MessageBodyFormat.TEXT;
        message.bodyText = text == null || text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
        message.sentAt = now;
        message.occurredAt = now;
        return message;
    }

    /**
     * A consultant's own email, waiting for {@code sendAt}. {@code reply} names the thread it answers and the message
     * in it, or is null for a first email in a thread of its own. {@code markdown} is what they wrote, tokens filled.
     */
    public static OutreachMessage scheduled(UUID workspaceId, UUID projectId, UUID personId, UUID candidateId,
                                            UUID senderUserId, String fromAddress, String toAddress, String subject,
                                            String html, String markdown, ThreadReply reply, Instant sendAt) {
        OutreachMessage message = new OutreachMessage();
        message.workspaceId = workspaceId;
        message.projectId = projectId;
        message.personId = personId;
        message.candidateId = candidateId;
        message.direction = MessageDirection.OUTBOUND;
        message.channel = MessageChannel.EMAIL;
        message.kind = MessageKind.MANUAL;
        message.status = MessageStatus.SCHEDULED;
        message.senderUserId = senderUserId;
        message.fromAddress = fromAddress;
        message.toAddress = toAddress;
        message.subject = subject;
        message.body = html;
        message.bodyFormat = MessageBodyFormat.MARKDOWN;
        message.bodyText = markdown;
        if (reply != null) {
            message.threadId = reply.threadId();
            message.threadGateway = reply.gateway();
            message.inReplyTo = reply.messageId();
        }
        message.sendAt = sendAt;
        message.occurredAt = sendAt;
        return message;
    }

    public boolean isScheduled() {
        return status == MessageStatus.SCHEDULED;
    }

    public void markSent(SentEmail sent, MailboxGatewayKind gateway, Instant now) {
        status = MessageStatus.SENT;
        providerMessageId = sent.messageId();
        threadId = threadId != null ? threadId : sent.threadId();
        threadGateway = gateway;
        sentAt = now;
        occurredAt = now;
        sendingSince = null;
    }

    /** It never went, and never will: a send is not retried. */
    public void fail(OutreachStopReason reason, Instant now) {
        status = MessageStatus.FAILED;
        failReason = reason;
        occurredAt = now;
        sendingSince = null;
    }

    public void cancel() {
        status = MessageStatus.CANCELLED;
        sendingSince = null;
    }

    /** Not yet: the daily cap is full, or the service asked us to wait. Nothing went. */
    public void deferTo(Instant due) {
        sendAt = due;
        occurredAt = due;
        sendingSince = null;
    }

    public void releaseClaim() {
        sendingSince = null;
    }

    /**
     * What came back into {@code thread}: a reply, or a mail server's bounce. {@code found} is null for a bounce a
     * webhook reported without a message of its own; the row then waits, as a placeholder, for the one a read finds.
     */
    public static OutreachMessage received(OutreachThread thread, MessageKind kind, ThreadMessage found,
                                           String mailboxAddress, Instant now) {
        OutreachMessage message = new OutreachMessage();
        message.workspaceId = thread.workspaceId();
        message.projectId = thread.projectId();
        message.personId = thread.personId();
        message.candidateId = thread.candidateId();
        message.enrollmentId = thread.enrollmentId();
        message.direction = MessageDirection.INBOUND;
        message.channel = MessageChannel.EMAIL;
        message.kind = kind;
        message.status = MessageStatus.RECEIVED;
        message.senderUserId = thread.senderUserId();
        message.toAddress = mailboxAddress;
        message.threadId = thread.threadId();
        message.threadGateway = thread.gateway();
        message.bodyFormat = MessageBodyFormat.TEXT;
        message.occurredAt = now;
        if (found != null) {
            message.completeWith(found);
        }
        return message;
    }

    /** A bounce's words are a mail server's, quoting our own email; only a reply's are kept. */
    public void completeWith(ThreadMessage found) {
        providerMessageId = found.providerMessageId();
        fromAddress = found.fromAddress();
        if (found.receivedAt() != null) {
            occurredAt = found.receivedAt();
        }
        if (kind == MessageKind.REPLY) {
            bodyText = found.text();
        }
    }
}
