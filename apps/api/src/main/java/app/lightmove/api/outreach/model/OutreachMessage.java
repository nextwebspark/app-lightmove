package app.lightmove.api.outreach.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.outreach.constant.MailboxGatewayKind;
import app.lightmove.api.outreach.constant.MessageBodyFormat;
import app.lightmove.api.outreach.constant.MessageChannel;
import app.lightmove.api.outreach.constant.MessageDirection;
import app.lightmove.api.outreach.constant.MessageKind;
import app.lightmove.api.outreach.constant.MessageStatus;
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
 * One line of an executive's conversation on a position (V101, widened by V144). So far always an outbound
 * sequence step, written once it went. Staff-only.
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
    @Column(name = "status", nullable = false, updatable = false)
    private MessageStatus status;

    @Column(name = "sender_user_id", nullable = false, updatable = false)
    private UUID senderUserId;

    @Column(name = "from_address", updatable = false)
    private String fromAddress;

    @Column(name = "to_address", updatable = false)
    private String toAddress;

    @Column(name = "provider_message_id", updatable = false)
    private String providerMessageId;

    @Column(name = "thread_id", updatable = false)
    private String threadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "thread_gateway", updatable = false)
    private MailboxGatewayKind threadGateway;

    @Column(name = "subject", updatable = false)
    private String subject;

    /** The HTML as it went; the screens read {@code bodyText}, never this. */
    @Column(name = "body", updatable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_format", nullable = false, updatable = false)
    private MessageBodyFormat bodyFormat;

    @Column(name = "body_text", updatable = false)
    private String bodyText;

    /** Set only on an outbound email that went: the daily cap counts these. */
    @Column(name = "sent_at", updatable = false)
    private Instant sentAt;

    /** Where the line sits in the conversation. */
    @Column(name = "occurred_at", nullable = false, updatable = false)
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
}
