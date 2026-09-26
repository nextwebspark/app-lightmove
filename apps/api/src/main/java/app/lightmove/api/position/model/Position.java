package app.lightmove.api.position.model;

import app.lightmove.api.common.constant.BaseSalaryMode;
import app.lightmove.api.common.constant.BonusBasis;
import app.lightmove.api.common.constant.DefaultCurrency;
import app.lightmove.api.common.constant.EmploymentType;
import app.lightmove.api.common.constant.IncentiveType;
import app.lightmove.api.common.constant.NoticeUnit;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.position.constant.FieldSource;
import app.lightmove.api.position.constant.MandateReason;
import app.lightmove.api.position.constant.PositionFieldKeys;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The position brief — the mandate's role definition, 1:1 with its project, seeded from the template
 * library and edited a step at a time. Publishing stamps who declared it ready; it is not a lock (V38).
 */
@Entity
@Table(name = "app_lm_position")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Position extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "department", length = 160)
    private String department;

    @Column(name = "location_city", length = 120)
    private String locationCity;

    @Column(name = "location_country", length = 120)
    private String locationCountry;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 80)
    private EmploymentType employmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "seniority", length = 16)
    private Seniority seniority;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_position_responsibility",
            joinColumns = @JoinColumn(name = "position_id"))
    @OrderColumn(name = "sort_order")
    private List<PositionResponsibility> responsibilities = new ArrayList<>();

    @Column(name = "narrative")
    private String narrative;

    /**
     * Provenance of the scalars a template or document reading can claim, keyed by wire field name; an
     * absent key means unclaimed and still fillable. Compensation and the role title are never in here.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_sources", nullable = false)
    private Map<String, FieldSource> fieldSources = Map.of();

    @Enumerated(EnumType.STRING)
    @Column(name = "mandate_reason", nullable = false, length = 32)
    private MandateReason mandateReason = MandateReason.NEW_ROLE;

    @Column(name = "business_driver")
    private String businessDriver;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_position_priority",
            joinColumns = @JoinColumn(name = "position_id"))
    @OrderColumn(name = "sort_order")
    private List<PositionPriority> strategicPriorities = new ArrayList<>();

    @Column(name = "confidential", nullable = false)
    private boolean confidential;

    @Column(name = "internal_context")
    private String internalContext;

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

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = DefaultCurrency.CODE;

    @Column(name = "salary_min")
    private Long salaryMin;

    @Column(name = "salary_max")
    private Long salaryMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_salary_mode", nullable = false, length = 16)
    private BaseSalaryMode baseSalaryMode = BaseSalaryMode.ANNUAL;

    @Column(name = "bonus_value", precision = 14, scale = 2)
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

    /** How much of the assessment the technical panel carries; the behavioural panel carries the rest. */
    @Column(name = "technical_share", nullable = false)
    private int technicalShare = 50;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    /** A blank brief at the client's home country, which applying a role template must leave alone. */
    public static Position forProject(UUID projectId, String country) {
        Position position = new Position();
        position.projectId = projectId;
        position.locationCountry = country;
        return position;
    }

    public void applyDetails(PositionDetails details) {
        this.department = details.department();
        this.locationCity = details.locationCity();
        this.locationCountry = details.locationCountry();
        this.employmentType = details.employmentType();
        this.seniority = details.seniority();
        this.narrative = details.narrative();
        replace(this.responsibilities, details.responsibilities());
        mergeFieldSources(PositionFieldKeys.DETAILS, details.fieldSources());
    }

    public void applyContext(MandateContext context) {
        this.mandateReason = context.mandateReason();
        this.businessDriver = context.businessDriver();
        this.confidential = context.confidential();
        this.internalContext = context.internalContext();
        replace(this.strategicPriorities, context.strategicPriorities());
        mergeFieldSources(PositionFieldKeys.CONTEXT, context.fieldSources());
    }

    public void applyReporting(ReportingStructure reporting) {
        this.teamSize = reporting.teamSize();
        this.noticeValue = reporting.noticeValue();
        this.noticeUnit = reporting.noticeUnit();
        replace(this.orgChart, mandateSeatFirst(reporting.orgChart()));
        mergeFieldSources(PositionFieldKeys.REPORTING, reporting.fieldSources());
    }

    /**
     * Replaces this step's own disjoint key slice: a step key the slice omits is removed rather than
     * left stale, and an incoming {@code DOCUMENT} never moves a key off {@code MANUAL}, so a person's
     * correction is never reclaimed by a stale client PUT.
     */
    private void mergeFieldSources(Set<String> stepKeys, Map<String, FieldSource> slice) {
        Map<String, FieldSource> merged = new LinkedHashMap<>(this.fieldSources);
        for (String key : stepKeys) {
            FieldSource incoming = slice.get(key);
            if (merged.get(key) == FieldSource.MANUAL && incoming == FieldSource.DOCUMENT) {
                continue;
            }
            if (incoming == null) {
                merged.remove(key);
            } else {
                merged.put(key, incoming);
            }
        }
        this.fieldSources = Map.copyOf(merged);
    }

    /**
     * Not cosmetic: Hibernate rewrites an {@code @OrderColumn} list in place row by row, so moving the
     * flagged seat between slots would briefly flag two rows and trip the one-seat partial unique index
     * (a 409). Keeping it at slot 0 on every write means the flag never moves.
     */
    private static List<PositionOrgNode> mandateSeatFirst(List<PositionOrgNode> chart) {
        return Stream.concat(
                        chart.stream().filter(PositionOrgNode::isMandateSeat),
                        chart.stream().filter(node -> !node.isMandateSeat()))
                .toList();
    }

    /** Absent only on a chart that has somehow lost its anchor. */
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

    /** A null share keeps the stored one: a write that names no split is not revising it. */
    public void replaceCompetencies(List<PositionCompetency> newCompetencies, Integer technicalShare) {
        replace(this.competencies, newCompetencies);
        if (technicalShare != null) {
            this.technicalShare = technicalShare;
        }
    }

    /** A repeat publish keeps the first stamp — the date can reach a client-facing document. */
    public void publish(UUID actorId) {
        if (publishedAt != null) {
            return;
        }
        // Truncated to timestamptz's microseconds, or this response disagrees with every later read.
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
