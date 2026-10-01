package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.customcolumn.model.CustomFieldValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private CandidateStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_assessment")
    private CandidateAiAssessment aiAssessment;

    /** The last AI enrichment run that produced nothing (V80); a later success clears it. */
    @Column(name = "ai_enrich_failed_at")
    private Instant aiEnrichFailedAt;

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

    public static Candidate mapped(UUID projectId, UUID addedBy, UUID triageCompanyId, Person person,
                                   CandidateSource source, CandidateDetails details) {
        Candidate candidate = new Candidate();
        candidate.projectId = projectId;
        candidate.addedBy = addedBy;
        candidate.person = person;
        candidate.source = source;
        candidate.sourceUrl = details.sourceUrl();
        candidate.triageCompanyId = triageCompanyId;
        candidate.describe(details);
        return candidate;
    }

    /** The mandate's half of a save: where the person works for this mandate, and how far along. */
    public void describe(CandidateDetails details) {
        this.status = details.status();
        this.companyName = details.employerName();
    }

    /** Replaces the last AI assessment whole — it is the model's own reading, not anybody's edit. */
    public void recordAiAssessment(CandidateAiAssessment assessment) {
        this.aiAssessment = assessment;
        this.aiEnrichFailedAt = null;
    }

    public void recordAiEnrichFailure() {
        this.aiEnrichFailedAt = Instant.now();
    }

    /**
     * Replaces the whole bag: {@code CustomColumnService.applyTo} has already merged it, and an
     * entity with a second opinion about which keys are real would be a second place to get it wrong.
     */
    public void describeCustomFields(CustomFieldValues values) {
        this.customFields = values == null ? CustomFieldValues.empty() : values;
    }

    public void moveTo(CandidateStatus newStatus) {
        this.status = newStatus;
    }

    /** Moves the person to another of the mandate's companies, or off the universe altogether. */
    public void remapTo(UUID newTriageCompanyId) {
        this.triageCompanyId = newTriageCompanyId;
    }

    /** Research resolved the employer into one of the mandate's companies — map and snapshot it. */
    public void employBy(UUID resolvedTriageCompanyId, String resolvedEmployerName) {
        this.triageCompanyId = resolvedTriageCompanyId;
        this.companyName = resolvedEmployerName;
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
