package app.lightmove.api.notetaker.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The questions a consultant prepared for one call with a person on a position (V154). The call is matched on its
 * start and title, as every teammate's copy of it is, because a calendar's re-read replaces its meeting rows.
 */
@Entity
@Table(name = "app_lm_interview_plan")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewPlan extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "person_id", nullable = false, updatable = false)
    private UUID personId;

    @Column(name = "candidate_id")
    private UUID candidateId;

    @Column(name = "meeting_id")
    private UUID meetingId;

    @Column(name = "title")
    private String title;

    @Column(name = "title_key", nullable = false, updatable = false)
    private String titleKey;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "questions", nullable = false, columnDefinition = "jsonb")
    private List<InterviewQuestion> questions;

    @Column(name = "drafted_at")
    private Instant draftedAt;

    @Column(name = "drafted_by")
    private UUID draftedBy;

    @Column(name = "saved_at")
    private Instant savedAt;

    @Column(name = "saved_by")
    private UUID savedBy;

    public static InterviewPlan forCall(UUID workspaceId, UUID projectId, UUID personId, UUID candidateId,
                                        UUID meetingId, String title, Instant startsAt) {
        InterviewPlan plan = new InterviewPlan();
        plan.workspaceId = workspaceId;
        plan.projectId = projectId;
        plan.personId = personId;
        plan.candidateId = candidateId;
        plan.meetingId = meetingId;
        plan.title = title;
        plan.titleKey = titleKeyOf(title);
        plan.startsAt = startsAt;
        plan.questions = List.of();
        return plan;
    }

    public static String titleKeyOf(String title) {
        String key = Objects.toString(title, "").trim().toLowerCase(Locale.ROOT);
        return key.length() <= 500 ? key : key.substring(0, 500);
    }

    public boolean isDrafted() {
        return draftedAt != null;
    }

    public void drafted(List<InterviewQuestion> drafted, UUID by, Instant at) {
        this.questions = List.copyOf(drafted);
        this.draftedBy = by;
        this.draftedAt = at;
    }

    public void saved(List<InterviewQuestion> edited, UUID by, Instant at) {
        this.questions = List.copyOf(edited);
        this.savedBy = by;
        this.savedAt = at;
    }

    /** The meeting row a calendar re-read made in place of the one the plan was made from. */
    public void seenAs(UUID meetingId, UUID candidateId) {
        this.meetingId = meetingId;
        if (candidateId != null) {
            this.candidateId = candidateId;
        }
    }

    public List<InterviewQuestion> includedQuestions() {
        return questions.stream().filter(InterviewQuestion::included).toList();
    }
}
