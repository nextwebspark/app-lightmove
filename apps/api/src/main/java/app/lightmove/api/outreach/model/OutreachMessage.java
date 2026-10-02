package app.lightmove.api.outreach.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One outreach email as it went (V101). Staff-only, and never a reply: those stay in the sender's inbox. */
@Entity
@Table(name = "app_lm_outreach_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutreachMessage extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;

    /** Zero-based, as the sequence numbers its steps. */
    @Column(name = "step", nullable = false, updatable = false)
    private int step;

    @Column(name = "sender_user_id", nullable = false, updatable = false)
    private UUID senderUserId;

    @Column(name = "provider_message_id", nullable = false, updatable = false)
    private String providerMessageId;

    @Column(name = "thread_id", updatable = false)
    private String threadId;

    @Column(name = "subject", nullable = false, updatable = false)
    private String subject;

    @Column(name = "body", nullable = false, updatable = false)
    private String body;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    public static OutreachMessage sent(OutreachEnrollment enrollment, int step, OutgoingEmail email, SentEmail sent,
                                       Instant now) {
        OutreachMessage message = new OutreachMessage();
        message.workspaceId = enrollment.getWorkspaceId();
        message.enrollmentId = enrollment.getId();
        message.step = step;
        message.senderUserId = enrollment.getSenderUserId();
        message.providerMessageId = sent.messageId();
        message.threadId = sent.threadId();
        message.subject = email.subject();
        message.body = email.htmlBody();
        message.sentAt = now;
        return message;
    }
}
