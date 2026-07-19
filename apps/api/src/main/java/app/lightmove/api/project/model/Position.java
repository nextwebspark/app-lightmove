package app.lightmove.api.project.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.project.constant.EmploymentType;
import app.lightmove.api.project.constant.MandateReason;
import app.lightmove.api.project.constant.NoticeUnit;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The position brief — the mandate's role definition, 1:1 with its project. Seeded from the
 * template library at project creation and edited by the Position screen's autosave.
 *
 * <p>The three lists are owned ordered values (replace-list writes), not entities. Locking freezes
 * the <i>whole</i> brief — every write path checks {@link #isLocked()} in the service — because a
 * locked brief is the benchmark candidate fit is scored against downstream. Only a project ADMIN
 * may unlock (the {@code POSITION_UNLOCK} action).
 */
@Entity
@Table(name = "app_lm_position")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Position extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mandate_reason", nullable = false, length = 32)
    private MandateReason mandateReason = MandateReason.NEW_ROLE;

    @Column(name = "internal_context")
    private String internalContext;

    @Column(name = "narrative")
    private String narrative;

    @Column(name = "reports_to", length = 160)
    private String reportsTo;

    @Column(name = "direct_reports")
    private Integer directReports;

    @Column(name = "team_size")
    private Integer teamSize;

    @Column(name = "location", length = 120)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 80)
    private EmploymentType employmentType;

    @Column(name = "salary_min")
    private Long salaryMin;

    @Column(name = "salary_max")
    private Long salaryMax;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "notice_value")
    private Integer noticeValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "notice_unit", length = 8)
    private NoticeUnit noticeUnit;

    @Column(name = "bonus_target_pct")
    private Integer bonusTargetPct;

    @Column(name = "ltip", length = 160)
    private String ltip;

    @Column(name = "confidential", nullable = false)
    private boolean confidential;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by")
    private UUID lockedBy;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_position_benefit",
            joinColumns = @JoinColumn(name = "position_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "label", nullable = false, length = 80)
    private List<String> benefits = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_position_criterion",
            joinColumns = @JoinColumn(name = "position_id"))
    @OrderColumn(name = "sort_order")
    private List<PositionCriterion> criteria = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_position_competency",
            joinColumns = @JoinColumn(name = "position_id"))
    @OrderColumn(name = "sort_order")
    private List<CompetencyRow> competencies = new ArrayList<>();

    public static Position forProject(UUID projectId) {
        Position position = new Position();
        position.projectId = projectId;
        return position;
    }

    /** Full scalar snapshot — the autosave PUT and the template seed both land here. */
    public void apply(PositionDetails details) {
        this.mandateReason = details.mandateReason();
        this.internalContext = details.internalContext();
        this.narrative = details.narrative();
        this.reportsTo = details.reportsTo();
        this.directReports = details.directReports();
        this.teamSize = details.teamSize();
        this.location = details.location();
        this.employmentType = details.employmentType();
        this.salaryMin = details.salaryMin();
        this.salaryMax = details.salaryMax();
        this.currency = details.currency();
        this.noticeValue = details.noticeValue();
        this.noticeUnit = details.noticeUnit();
        this.bonusTargetPct = details.bonusTargetPct();
        this.ltip = details.ltip();
        this.confidential = details.confidential();
        this.benefits.clear();
        this.benefits.addAll(details.benefits());
    }

    public void replaceCriteria(List<PositionCriterion> newCriteria) {
        this.criteria.clear();
        this.criteria.addAll(newCriteria);
    }

    public void replaceCompetencies(List<CompetencyRow> newCompetencies) {
        this.competencies.clear();
        this.competencies.addAll(newCompetencies);
    }

    public void lock(UUID userId, Instant at) {
        this.lockedAt = at;
        this.lockedBy = userId;
    }

    public void unlock() {
        this.lockedAt = null;
        this.lockedBy = null;
    }

    public boolean isLocked() {
        return lockedAt != null;
    }
}
