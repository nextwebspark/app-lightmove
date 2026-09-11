package app.lightmove.api.triagecompany.model;

import app.lightmove.api.triagecompany.constant.TriageCompanySource;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.customcolumn.model.CustomFieldValues;
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
 * A company a mandate has taken a position on — the row "Add to Universe" writes, the Companies
 * screens move between stages, and Delete removes. There is no untriaged state: a company nobody has
 * acted on has no row here.
 *
 * <p>The snapshot columns are the same contract as {@link StrategyCompanyRef}'s, with one addition:
 * {@code note} is the consultant's remark on this company <i>for this mandate</i>, unlike Apollo's
 * {@code short_description}. {@code apolloAccountId} is null when there is no universe id to carry,
 * and V34's CHECK keeps the pair honest — a {@code STRATEGY} row without an id cannot exist.
 *
 * <p>{@code status} and {@code source} are stored as enum names, not wire tokens, matching the CHECK
 * constraints in V32 and V34: the wire token is the client's vocabulary and is free to change.
 */
@Entity
@Table(name = "app_lm_project_triage_company")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TriageCompany extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    /** Null with no universe id to carry. A capture may have one, so {@code source} is not a proxy. */
    @Column(name = "apollo_account_id", updatable = false)
    private String apolloAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16, updatable = false)
    private TriageCompanySource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TriageCompanyStatus status;

    @Column(name = "note")
    private String note;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "industry")
    private String industry;

    @Column(name = "company_country")
    private String companyCountry;

    @Column(name = "company_city")
    private String companyCity;

    @Column(name = "num_employees")
    private Integer numEmployees;

    @Column(name = "annual_revenue")
    private Long annualRevenue;

    @Column(name = "website")
    private String website;

    @Column(name = "company_linkedin_url")
    private String companyLinkedinUrl;

    @Column(name = "founded_year")
    private Integer foundedYear;

    @Column(name = "short_description")
    private String shortDescription;

    /** The page the plugin captured this from. Null for every other source. */
    @Column(name = "source_url", updatable = false)
    private String sourceUrl;

    @Column(name = "logo_url")
    private String logoUrl;

    /**
     * Values for this project's COMPANY custom columns, keyed by the column's {@code field_key}.
     * Nothing here validates a key: {@code CustomColumnService.applyTo} is the only writer.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", nullable = false)
    private CustomFieldValues customFields = CustomFieldValues.empty();

    @Column(name = "added_by", nullable = false, updatable = false)
    private UUID addedBy;

    /**
     * A company the mandate supplied itself. Not for {@link TriageCompanySource#STRATEGY} rows —
     * {@code TriageCompanyWriter} writes those in one multi-row statement so a bulk add can skip what
     * the mandate already holds rather than racing a read against it.
     */
    public static TriageCompany captured(UUID projectId, UUID addedBy, TriageCompanySource source,
                                         TriageCompanyStatus status, CapturedCompanyDetails details) {
        TriageCompany company = new TriageCompany();
        company.projectId = projectId;
        company.addedBy = addedBy;
        company.source = source;
        company.status = status;
        company.companyName = details.companyName();
        company.industry = details.industry();
        company.companyCountry = details.companyCountry();
        company.companyCity = details.companyCity();
        company.numEmployees = details.numEmployees();
        company.annualRevenue = details.annualRevenue();
        company.website = details.website();
        company.companyLinkedinUrl = details.companyLinkedinUrl();
        company.foundedYear = details.foundedYear();
        company.shortDescription = details.shortDescription();
        company.logoUrl = details.logoUrl();
        company.sourceUrl = details.sourceUrl();
        company.annotate(details.note());
        return company;
    }

    /**
     * Fills in what research found, and only where nobody has filled anything in — a consultant's
     * capture never loses a field to a vendor. Name, note, stage and provenance are never touched.
     */
    public void enrichFacts(CapturedCompanyDetails details) {
        if (industry == null) {
            industry = details.industry();
        }
        if (companyCountry == null) {
            companyCountry = details.companyCountry();
        }
        if (companyCity == null) {
            companyCity = details.companyCity();
        }
        if (numEmployees == null) {
            numEmployees = details.numEmployees();
        }
        if (annualRevenue == null) {
            annualRevenue = details.annualRevenue();
        }
        if (website == null) {
            website = details.website();
        }
        if (companyLinkedinUrl == null) {
            companyLinkedinUrl = details.companyLinkedinUrl();
        }
        if (foundedYear == null) {
            foundedYear = details.foundedYear();
        }
        if (shortDescription == null) {
            shortDescription = details.shortDescription();
        }
        if (logoUrl == null) {
            logoUrl = details.logoUrl();
        }
    }

    /**
     * Replaces the company's own facts. Only reached for a company the mandate supplied itself, and
     * {@code apolloAccountId}, {@code source} and {@code sourceUrl} are {@code updatable = false} so
     * provenance cannot travel through here by mistake. {@code note} has its own write.
     */
    public void describe(CapturedCompanyDetails details) {
        this.companyName = details.companyName();
        this.industry = details.industry();
        this.companyCountry = details.companyCountry();
        this.companyCity = details.companyCity();
        this.numEmployees = details.numEmployees();
        this.annualRevenue = details.annualRevenue();
        this.website = details.website();
        this.companyLinkedinUrl = details.companyLinkedinUrl();
        this.foundedYear = details.foundedYear();
        this.shortDescription = details.shortDescription();
    }

    /**
     * True for a company the mandate supplied itself, which is the only kind it may rewrite.
     *
     * <p><b>Keyed on the snapshot, not the door.</b> {@code source != STRATEGY} used to mean the same
     * thing, because only Strategy wrote market rows — until a plugin capture began resolving against
     * the universe and landing a full snapshot badged EXTENSION or MANUAL. Those rows carry an
     * {@code apolloAccountId} the ETL owns and re-keys, so rewriting one is rewriting the export;
     * what a mandate may edit is the row nobody else authored.
     */
    public boolean isMandateSupplied() {
        return apolloAccountId == null;
    }

    public void moveTo(TriageCompanyStatus newStatus) {
        this.status = newStatus;
    }

    /**
     * Replaces the whole bag: {@code CustomColumnService.applyTo} has already merged it, and an
     * entity with a second opinion about which keys are real would be a second place to get it wrong.
     */
    public void describeCustomFields(CustomFieldValues values) {
        this.customFields = values == null ? CustomFieldValues.empty() : values;
    }

    /** Blank clears the note rather than storing an empty string, so "has a note" stays a null check. */
    public void annotate(String newNote) {
        this.note = newNote == null || newNote.isBlank() ? null : newNote.trim();
    }
}
