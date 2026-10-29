package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.customcolumn.model.CustomFieldValues;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A {@link Person} on one mandate — the other half of a talent map, beside the companies
 * {@code triagecompany} holds.
 *
 * <p><b>The person is the workspace's; this row is the mandate's.</b> Status, note, the mandate's own
 * custom-column values and the AI assessment against its brief live here, so two mandates can disagree
 * about one executive while sharing everything true of them (V91).
 *
 * <p>{@code companyName} is a write-time snapshot that outlives the mapping. V36's
 * {@code ON DELETE SET NULL} is the other half of that pair: removing a company from a mandate must
 * not silently delete the people mapped at it.
 */
@Entity
@Table(name = "app_lm_project_candidate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Candidate extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false, updatable = false)
    private Person person;

    /** Null when the person's employer is not one of the mandate's triaged companies. */
    @Column(name = "triage_company_id")
    private UUID triageCompanyId;

    @Column(name = "company_name")
    private String companyName;

    /**
     * The Companies stage this person is listed under (V150): their company's when filed or remapped, then their
     * own, so moving one executive at a company leaves the colleagues where they stand.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "triage_status", nullable = false, length = 16)
    private TriageCompanyStatus triageStatus = TriageCompanyStatus.IN_UNIVERSE;

    /** The one fact this row holds about where they stand; the stage is read through it (V131). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private CandidateStatusDefinition status;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    /**
     * Values for this project's CANDIDATE custom columns, keyed by the column's {@code field_key}.
     * The columns are the mandate's, so their values are too.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", nullable = false)
    private CustomFieldValues customFields = CustomFieldValues.empty();

    /** The door this mandate got the person through; the person keeps the first. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16, updatable = false)
    private CandidateSource source;

    /** The profile page the plugin read this from. Null for every other source. */
    @Column(name = "source_url", updatable = false)
    private String sourceUrl;

    @Column(name = "added_by", nullable = false, updatable = false)
    private UUID addedBy;

    /**
     * Where the Pipeline page's manual order puts them within their stage (V132). Fractional, so a drag
     * writes the one row moved; null until anyone drags them, which sorts after every ranked row.
     */
    @Column(name = "pipeline_rank")
    private BigDecimal pipelineRank;

    /** False hides them from a client seat on every read it can reach; staff always see them (V132). */
    @Column(name = "client_visible", nullable = false)
    private boolean clientVisible = true;

    public static Candidate mapped(UUID projectId, UUID addedBy, UUID triageCompanyId,
                                   TriageCompanyStatus companyStatus, Person person,
                                   CandidateSource source, CandidateStatusDefinition status,
                                   CandidateDetails details) {
        Candidate candidate = new Candidate();
        candidate.status = status;
        candidate.statusChangedAt = Instant.now();
        candidate.projectId = projectId;
        candidate.addedBy = addedBy;
        candidate.person = person;
        candidate.source = source;
        candidate.sourceUrl = details.sourceUrl();
        candidate.triageCompanyId = triageCompanyId;
        candidate.triageStatus = companyStatus;
        candidate.describe(details);
        return candidate;
    }

    /**
     * Someone the workspace already holds, added to a mandate from the Candidates page: at the catalog's
     * starting status, by hand, at the employer they were last recorded at, and on none of the mandate's
     * companies.
     */
    public static Candidate mappedFromPool(UUID projectId, UUID addedBy, Person person, String employerName,
                                           CandidateStatusDefinition startingStatus) {
        Candidate candidate = new Candidate();
        candidate.projectId = projectId;
        candidate.addedBy = addedBy;
        candidate.person = person;
        candidate.source = CandidateSource.MANUAL;
        candidate.status = startingStatus;
        candidate.statusChangedAt = Instant.now();
        candidate.companyName = employerName;
        return candidate;
    }

    /** The mandate's half of a save: where the person works for this mandate. */
    public void describe(CandidateDetails details) {
        this.companyName = details.employerName();
    }

    /**
     * Replaces the whole bag: {@code CustomColumnService.applyTo} has already merged it, and an
     * entity with a second opinion about which keys are real would be a second place to get it wrong.
     */
    public void describeCustomFields(CustomFieldValues values) {
        this.customFields = values == null ? CustomFieldValues.empty() : values;
    }

    /** Answers whether anything changed; a move to the status already held keeps its date. */
    public boolean moveTo(CandidateStatusDefinition newStatus) {
        if (status != null && status.getId().equals(newStatus.getId())) {
            return false;
        }
        this.status = newStatus;
        this.statusChangedAt = Instant.now();
        return true;
    }

    public void rankAt(BigDecimal newRank) {
        this.pipelineRank = newRank;
    }

    /** Answers whether anything changed. */
    public boolean showToClient(boolean visible) {
        boolean changed = clientVisible != visible;
        this.clientVisible = visible;
        return changed;
    }

    /**
     * Moves the person to another of the mandate's companies, or off the universe altogether. A new company lends
     * them its stage; staying at the same one keeps the stage they were moved to.
     */
    public void remapTo(UUID newTriageCompanyId, TriageCompanyStatus newCompanyStatus) {
        if (!Objects.equals(triageCompanyId, newTriageCompanyId)) {
            this.triageStatus = newCompanyStatus;
        }
        this.triageCompanyId = newTriageCompanyId;
    }

    /** Research resolved the employer into one of the mandate's companies — map, snapshot and stage it. */
    public void employBy(UUID resolvedTriageCompanyId, String resolvedEmployerName,
                         TriageCompanyStatus resolvedCompanyStatus) {
        remapTo(resolvedTriageCompanyId, resolvedCompanyStatus);
        this.companyName = resolvedEmployerName;
    }

    /** Answers whether anything changed. Only this person moves; their company and its other people stay. */
    public boolean moveToStage(TriageCompanyStatus newTriageStatus) {
        boolean changed = triageStatus != newTriageStatus;
        this.triageStatus = newTriageStatus;
        return changed;
    }

    /**
     * Research named an employer and this mandate records none: take it. A mapped row's name is the
     * triage snapshot and is never overwritten.
     */
    public void adoptEmployer(String researchedEmployerName) {
        if (triageCompanyId == null && companyName == null) {
            companyName = researchedEmployerName;
        }
    }
}
