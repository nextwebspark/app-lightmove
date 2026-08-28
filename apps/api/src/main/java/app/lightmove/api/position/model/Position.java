package app.lightmove.api.position.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.position.constant.BaseSalaryMode;
import app.lightmove.api.position.constant.BonusBasis;
import app.lightmove.api.position.constant.EmploymentType;
import app.lightmove.api.position.constant.HiringUrgency;
import app.lightmove.api.position.constant.IncentiveType;
import app.lightmove.api.position.constant.MandateReason;
import app.lightmove.api.position.constant.NoticeUnit;
import app.lightmove.api.position.constant.PositionSeniority;
import app.lightmove.api.position.constant.StrategicPriority;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The position brief — the mandate's role definition, 1:1 with its project. Seeded from the template
 * library when the project is created, then edited a wizard step at a time.
 *
 * <p>One apply method per step, rather than one taking the whole document. That is not tidiness: the
 * brief holds six adjacent {@code Long} salary and incentive figures, two {@code Integer} counts and
 * several same-typed strings, and a single positional constructor over all of them is a transposition
 * the compiler cannot see. A step-shaped record can only be filled from its own step.
 *
 * <p>Publishing stamps who declared the brief ready and when. It is not a lock — V38 retired that —
 * so every write above stays available afterwards.
 */
@Entity
@Table(name = "app_lm_position")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Position extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    // ── Step 1 · Position details ───────────────────────────────────────────

    @Column(name = "department", length = 160)
    private String department;

    @Column(name = "location", length = 120)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 80)
    private EmploymentType employmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "seniority", length = 16)
    private PositionSeniority seniority;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_position_responsibility",
            joinColumns = @JoinColumn(name = "position_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "text", nullable = false, length = 200)
    private List<String> responsibilities = new ArrayList<>();

    @Column(name = "narrative")
    private String narrative;

    // ── Step 2 · Mandate context ────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "mandate_reason", nullable = false, length = 32)
    private MandateReason mandateReason = MandateReason.NEW_ROLE;

    @Column(name = "business_driver")
    private String businessDriver;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_position_priority",
            joinColumns = @JoinColumn(name = "position_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 32)
    private Set<StrategicPriority> strategicPriorities = EnumSet.noneOf(StrategicPriority.class);

    @Enumerated(EnumType.STRING)
    @Column(name = "hiring_urgency", nullable = false, length = 16)
    private HiringUrgency hiringUrgency = HiringUrgency.STANDARD;

    @Column(name = "confidential", nullable = false)
    private boolean confidential;

    @Column(name = "internal_context")
    private String internalContext;

    // ── Step 3 · Reporting structure ────────────────────────────────────────

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_position_org_node",
            joinColumns = @JoinColumn(name = "position_id"))
    @OrderColumn(name = "sort_order")
    private List<PositionOrgNode> orgChart = new ArrayList<>();

    @Column(name = "team_size", length = 160)
    private String teamSize;

    @Column(name = "notice_value")
    private Integer noticeValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "notice_unit", length = 8)
    private NoticeUnit noticeUnit;

    // ── Step 4 · Compensation package ───────────────────────────────────────

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "salary_min")
    private Long salaryMin;

    @Column(name = "salary_max")
    private Long salaryMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_salary_mode", nullable = false, length = 16)
    private BaseSalaryMode baseSalaryMode = BaseSalaryMode.ANNUAL;

    @Column(name = "bonus_value", precision = 6, scale = 2)
    private BigDecimal bonusValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "bonus_basis", length = 24)
    private BonusBasis bonusBasis;

    @Enumerated(EnumType.STRING)
    @Column(name = "incentive_type", length = 24)
    private IncentiveType incentiveType;

    @Column(name = "incentive_amount")
    private Long incentiveAmount;

    @Column(name = "incentive_vesting", length = 200)
    private String incentiveVesting;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_position_benefit",
            joinColumns = @JoinColumn(name = "position_id"))
    @OrderColumn(name = "sort_order")
    private List<PositionBenefit> benefits = new ArrayList<>();

    // ── Step 5 · Assessment criteria ────────────────────────────────────────

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_position_criterion",
            joinColumns = @JoinColumn(name = "position_id"))
    @OrderColumn(name = "sort_order")
    private List<PositionCriterion> criteria = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_position_competency",
            joinColumns = @JoinColumn(name = "position_id"))
    @OrderColumn(name = "sort_order")
    private List<PositionCompetency> competencies = new ArrayList<>();

    // ── Step 6 · Publication ────────────────────────────────────────────────

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    public static Position forProject(UUID projectId) {
        Position position = new Position();
        position.projectId = projectId;
        return position;
    }

    public void applyDetails(PositionDetails details) {
        this.department = details.department();
        this.location = details.location();
        this.employmentType = details.employmentType();
        this.seniority = details.seniority();
        this.narrative = details.narrative();
        replace(this.responsibilities, details.responsibilities());
    }

    public void applyContext(MandateContext context) {
        this.mandateReason = context.mandateReason();
        this.businessDriver = context.businessDriver();
        this.hiringUrgency = context.hiringUrgency();
        this.confidential = context.confidential();
        this.internalContext = context.internalContext();
        this.strategicPriorities.clear();
        this.strategicPriorities.addAll(context.strategicPriorities());
    }

    public void applyReporting(ReportingStructure reporting) {
        this.teamSize = reporting.teamSize();
        this.noticeValue = reporting.noticeValue();
        this.noticeUnit = reporting.noticeUnit();
        replace(this.orgChart, reporting.orgChart());
    }

    /** The seat this brief is for. Absent only on a chart that has somehow lost its anchor. */
    public Optional<PositionOrgNode> mandateSeat() {
        return orgChart.stream().filter(PositionOrgNode::isMandateSeat).findFirst();
    }

    public void applyCompensation(CompensationPackage compensation) {
        this.currency = compensation.currency();
        this.salaryMin = compensation.salaryMin();
        this.salaryMax = compensation.salaryMax();
        this.baseSalaryMode = compensation.baseSalaryMode();
        this.bonusValue = compensation.bonusValue();
        this.bonusBasis = compensation.bonusBasis();
        this.incentiveType = compensation.incentiveType();
        this.incentiveAmount = compensation.incentiveAmount();
        this.incentiveVesting = compensation.incentiveVesting();
        replace(this.benefits, compensation.benefits());
    }

    public void replaceCriteria(List<PositionCriterion> newCriteria) {
        replace(this.criteria, newCriteria);
    }

    public void replaceCompetencies(List<PositionCompetency> newCompetencies) {
        replace(this.competencies, newCompetencies);
    }

    /**
     * Records that somebody declared the brief ready, once. A repeat publish keeps the first stamp:
     * the date can end up on a client-facing document, and a stray second click must not rewrite it.
     * When the brief last changed is {@code updatedAt}, which is a different question.
     */
    public void publish(UUID actorId) {
        if (publishedAt != null) {
            return;
        }
        // Truncated to what Postgres stores: timestamptz keeps microseconds, and an untruncated
        // Instant makes the response to this call disagree with every read of the same row afterwards.
        this.publishedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.publishedBy = actorId;
    }

    public void withdrawPublication() {
        this.publishedAt = null;
        this.publishedBy = null;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    /** Owned lists are replaced in place — Hibernate tracks the collection, not the reference. */
    private static <T> void replace(List<T> owned, List<T> replacement) {
        owned.clear();
        owned.addAll(replacement);
    }
}
