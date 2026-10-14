package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.ScorecardStatus;
import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A consultant's assessment of one mapped executive against the position's brief (V135): a score per competency
 * and a written report. Staff-only, like the AI assessment beside it — it ranks a person — so it never rides
 * {@code CandidateResponse}, which a client seat reads.
 */
@Entity
@Table(name = "app_lm_candidate_scorecard")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandidateScorecard extends BaseEntity {

    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 10;
    public static final int MAX_EVIDENCE = 2000;
    public static final int MAX_REPORT_SECTION = 4000;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ScorecardStatus status = ScorecardStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scores", nullable = false)
    private List<CriterionScore> scores = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report", nullable = false)
    private ScorecardReport report = ScorecardReport.empty();

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    public static CandidateScorecard startFor(Candidate candidate, UUID workspaceId) {
        CandidateScorecard scorecard = new CandidateScorecard();
        scorecard.workspaceId = workspaceId;
        scorecard.projectId = candidate.getProjectId();
        scorecard.candidateId = candidate.getId();
        return scorecard;
    }

    /**
     * Writes what was sent over what was held. Marking it reviewed stamps who and when; any other save is a draft
     * again, since what was reviewed is no longer what is on file.
     */
    public void record(List<CriterionScore> newScores, ScorecardReport newReport, boolean markReviewed, UUID by,
                       Instant at) {
        this.scores = new ArrayList<>(newScores);
        this.report = newReport;
        this.updatedBy = by;
        if (markReviewed) {
            this.status = ScorecardStatus.REVIEWED;
            this.reviewedBy = by;
            this.reviewedAt = at;
        } else {
            this.status = ScorecardStatus.DRAFT;
            this.reviewedBy = null;
            this.reviewedAt = null;
        }
    }
}
