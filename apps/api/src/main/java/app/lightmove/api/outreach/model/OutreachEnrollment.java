package app.lightmove.api.outreach.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.outreach.constant.EnrollmentStatus;
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
}
