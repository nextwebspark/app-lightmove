package app.lightmove.api.outreach.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.outreach.constant.EnrollmentStatus;
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
 * One person on one sequence (V100). The first email is frozen here as the consultant reviewed it, so
 * an edit to one person's opener reaches that person's email and nobody else's.
 */
@Entity
@Table(name = "app_lm_outreach_enrollment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutreachEnrollment extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "sequence_id", nullable = false, updatable = false)
    private UUID sequenceId;

    /** Null once the person is removed from the position; the row stays as the record of the approach. */
    @Column(name = "candidate_id")
    private UUID candidateId;

    @Column(name = "person_id", nullable = false, updatable = false)
    private UUID personId;

    @Column(name = "sender_user_id", nullable = false, updatable = false)
    private UUID senderUserId;

    @Column(name = "to_address", nullable = false, length = 320)
    private String toAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private EnrollmentStatus status;

    /** Zero-based: the step the dispatcher sends next. */
    @Column(name = "next_step", nullable = false)
    private int nextStep;

    @Column(name = "next_send_at")
    private Instant nextSendAt;

    @Column(name = "opener")
    private String opener;

    @Column(name = "opener_edited", nullable = false)
    private boolean openerEdited;

    @Column(name = "first_subject", nullable = false)
    private String firstSubject;

    @Column(name = "first_body", nullable = false)
    private String firstBody;

    @Column(name = "enrolled_by", updatable = false)
    private UUID enrolledBy;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt;

    /** The mail service's thread, set by the first send; the follow-ups and the reply land in it. */
    @Column(name = "thread_id")
    private String threadId;

    /** The last email sent, which the next follow-up replies to. */
    @Column(name = "last_message_id")
    private String lastMessageId;

    @Column(name = "last_sent_at")
    private Instant lastSentAt;

    @Column(name = "replied_at")
    private Instant repliedAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_reason", length = 24)
    private OutreachStopReason stopReason;

    /** The dispatcher's claim — written by {@code OutreachEnrollmentClaims}, cleared here when the claim ends. */
    @Column(name = "sending_since")
    private Instant sendingSince;

    /** The executive booked through the consultant's link (V104), rather than a consultant booking for them. */
    @Column(name = "booked_via_link", nullable = false)
    private boolean bookedViaLink;

    /** Due at once: the dispatcher holds it to the sender's sending window and daily cap. */
    public static OutreachEnrollment scheduled(OutreachSequence sequence, UUID candidateId, UUID personId,
                                               UUID sender, String toAddress, ReviewedFirstEmail email,
                                               Instant now) {
        OutreachEnrollment enrollment = new OutreachEnrollment();
        enrollment.workspaceId = sequence.getWorkspaceId();
        enrollment.projectId = sequence.getProjectId();
        enrollment.sequenceId = sequence.getId();
        enrollment.candidateId = candidateId;
        enrollment.personId = personId;
        enrollment.senderUserId = sender;
        enrollment.toAddress = toAddress;
        enrollment.status = EnrollmentStatus.SCHEDULED;
        enrollment.nextStep = 0;
        enrollment.nextSendAt = now;
        enrollment.opener = email.opener();
        enrollment.openerEdited = email.openerEdited();
        enrollment.firstSubject = email.subject();
        enrollment.firstBody = email.body();
        enrollment.enrolledBy = sender;
        enrollment.enrolledAt = now;
        return enrollment;
    }

    public boolean isLive() {
        return EnrollmentStatus.LIVE.contains(status);
    }

    /** How many of the sequence's emails have gone. */
    public int sentCount() {
        return nextStep;
    }

    /**
     * Records the step that just went. {@code nextSendAt} is when the following step is due, or null
     * when that was the last one.
     */
    public void markSent(SentEmail sent, Instant now, Instant followingStepDue) {
        if (threadId == null) {
            this.threadId = sent.threadId();
        }
        this.lastMessageId = sent.messageId();
        this.lastSentAt = now;
        this.nextStep = nextStep + 1;
        this.sendingSince = null;
        // A Stop pressed while this email was in flight stands: the email went, nothing after it will.
        if (isLive()) {
            this.nextSendAt = followingStepDue;
            this.status = followingStepDue == null ? EnrollmentStatus.COMPLETED : EnrollmentStatus.ACTIVE;
        }
    }

    /** Not yet: outside the sender's window or over their day's cap. The claim is released. */
    public void deferTo(Instant due) {
        this.nextSendAt = due;
        this.sendingSince = null;
    }

    /** Lets the next dispatch look at this row again; nothing was sent under the claim. */
    public void releaseClaim() {
        this.sendingSince = null;
    }

    /** The sequence was cut shorter than this person had got through: nothing is left to send. */
    public void complete() {
        this.status = EnrollmentStatus.COMPLETED;
        this.nextSendAt = null;
        this.sendingSince = null;
    }

    public void stop(OutreachStopReason reason, Instant now) {
        this.status = EnrollmentStatus.STOPPED;
        this.stopReason = reason;
        this.stoppedAt = now;
        this.nextSendAt = null;
        this.sendingSince = null;
    }

    /**
     * Clears any claim too: a reply can land while the row waits in a dispatcher's batch, and a claim left
     * behind would later be taken for a crashed send.
     */
    public void replied(Instant now) {
        this.status = EnrollmentStatus.REPLIED;
        this.repliedAt = now;
        this.nextSendAt = null;
        this.sendingSince = null;
    }

    public void bounced(Instant now) {
        this.status = EnrollmentStatus.BOUNCED;
        this.stoppedAt = now;
        this.nextSendAt = null;
        this.sendingSince = null;
    }

    /** A call was booked with the person; like a reply, nothing more goes. */
    public void booked(Instant now, boolean viaLink) {
        this.bookedViaLink = viaLink;
        this.status = EnrollmentStatus.BOOKED;
        this.stoppedAt = now;
        this.nextSendAt = null;
        this.sendingSince = null;
    }

    /** A reply or a bounce still counts after the last step has gone; only a stopped run is deaf. */
    public boolean isListening() {
        return isLive() || status == EnrollmentStatus.COMPLETED;
    }
}
