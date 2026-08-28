package app.lightmove.api.candidate.model;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An executive mapped for a mandate — the other half of a talent map, beside the companies
 * {@code triagecompany} holds.
 *
 * <p><b>The project is the mapping; the company is optional.</b> A candidate belongs to the mandate
 * they were researched for, because the note, the status and the compensation reading are all
 * mandate-specific — researching the same person for two mandates is two rows. Most executives are
 * found at a company already in the mandate's universe and carry {@code triageCompanyId}; a researcher
 * also meets people at companies the universe does not carry, and those rows carry none.
 *
 * <p>{@code companyName} is a write-time snapshot of the employer, and it outlives the mapping. V36's
 * {@code ON DELETE SET NULL} is the other half of that pair: removing a company from a mandate drops
 * the mandate's decision about the company and must not silently delete the people mapped at it, so
 * they fall back to unmapped rows that still say where the person works.
 *
 * <p>{@code status}, {@code seniorityLevel} and {@code source} are stored as their enum names rather
 * than their wire tokens, matching V36's CHECK constraints — {@code N-1} is not a legal identifier,
 * and the wire token is the client's vocabulary while the stored name is the schema's.
 */
@Entity
@Table(name = "app_lm_project_candidate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Candidate extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    /** Null when the person's employer is not one of the mandate's triaged companies. */
    @Column(name = "triage_company_id")
    private UUID triageCompanyId;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "title")
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "seniority_level", length = 16)
    private Seniority seniorityLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private CandidateStatus status;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "location_country")
    private String locationCountry;

    @Column(name = "location_city")
    private String locationCity;

    @Column(name = "nationality")
    private String nationality;

    @Column(name = "years_experience")
    private Integer yearsExperience;

    @Column(name = "summary")
    private String summary;

    @Column(name = "note")
    private String note;

    @Column(name = "compensation_currency", length = 3)
    private String compensationCurrency;

    @Column(name = "base_salary")
    private Long baseSalary;

    @Column(name = "bonus")
    private Long bonus;

    @Column(name = "allowances")
    private Long allowances;

    @Column(name = "long_term_incentive")
    private Long longTermIncentive;

    @Column(name = "notice_period")
    private String noticePeriod;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile", nullable = false)
    private CandidateProfile profile = CandidateProfile.empty();

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16, updatable = false)
    private CandidateSource source;

    /** The profile page the plugin read this from. Null for every other source. */
    @Column(name = "source_url", updatable = false)
    private String sourceUrl;

    @Column(name = "added_by", nullable = false, updatable = false)
    private UUID addedBy;

    public static Candidate mapped(UUID projectId, UUID addedBy, UUID triageCompanyId,
                                   CandidateSource source, CandidateDetails details) {
        Candidate candidate = new Candidate();
        candidate.projectId = projectId;
        candidate.addedBy = addedBy;
        candidate.source = source;
        candidate.sourceUrl = details.sourceUrl();
        candidate.triageCompanyId = triageCompanyId;
        candidate.describe(details);
        return candidate;
    }

    /**
     * Replaces every editable field. The drawer that edits a candidate submits all of them every time,
     * so a full replace is what actually happened — a field-by-field merge would have to invent a
     * meaning for an omitted field across twenty of them, and "omitted" and "cleared" would become the
     * same request.
     */
    public void describe(CandidateDetails details) {
        this.fullName = details.fullName();
        this.title = details.title();
        this.seniorityLevel = details.seniority();
        this.status = details.status();
        this.companyName = details.employerName();
        this.email = details.email();
        this.phone = details.phone();
        this.linkedinUrl = details.linkedinUrl();
        this.locationCountry = details.locationCountry();
        this.locationCity = details.locationCity();
        this.nationality = details.nationality();
        this.yearsExperience = details.yearsExperience();
        this.summary = details.summary();
        this.note = details.note();
        this.compensationCurrency = details.compensation().currency();
        this.baseSalary = details.compensation().baseSalary();
        this.bonus = details.compensation().bonus();
        this.allowances = details.compensation().allowances();
        this.longTermIncentive = details.compensation().longTermIncentive();
        this.noticePeriod = details.compensation().noticePeriod();
        this.profile = details.profile();
    }

    /** Moves the person along the line, leaving the rest of the profile as it was. */
    public void moveTo(CandidateStatus newStatus) {
        this.status = newStatus;
    }

    /** Moves the person to another of the mandate's companies, or off the universe altogether. */
    public void remapTo(UUID newTriageCompanyId) {
        this.triageCompanyId = newTriageCompanyId;
    }

    public CandidateCompensation compensation() {
        return new CandidateCompensation(compensationCurrency, baseSalary, bonus, allowances,
                longTermIncentive, noticePeriod);
    }
}
