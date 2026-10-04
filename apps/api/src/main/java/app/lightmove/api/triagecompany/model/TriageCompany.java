package app.lightmove.api.triagecompany.model;

import app.lightmove.api.common.industry.model.ResolvedIndustry;
import app.lightmove.api.common.industry.service.Industries;
import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.customcolumn.model.CustomFieldValues;
import app.lightmove.api.triagecompany.constant.TriageCompanySource;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
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
 * A mandate's decision about one company, with a write-time snapshot; no row means untriaged.
 * {@code status} and {@code source} are stored as enum names (V32/V34 CHECKs), never wire tokens.
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

    /** "Nobody here fits" — orthogonal to {@code status}; cleared once an executive is mapped here. */
    @Column(name = "no_executive_found", nullable = false)
    private boolean noExecutiveFound = false;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "industry")
    private String industry;

    /** Derived from {@link #industry} by {@link #fileUnder}, never written separately. */
    @Column(name = "industry_v2_code")
    private Integer industryV2Code;

    @Column(name = "industry_v2_label")
    private String industryV2Label;

    @Column(name = "sector_group")
    private String sectorGroup;

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

    /** Keyed by {@code field_key}; unvalidated here because {@code CustomColumnService.applyTo} is the only writer. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", nullable = false)
    private CustomFieldValues customFields = CustomFieldValues.empty();

    @Column(name = "added_by", nullable = false, updatable = false)
    private UUID addedBy;

    /** Not for {@link TriageCompanySource#STRATEGY} rows, which {@code TriageCompanyWriter} inserts race-free. */
    public static TriageCompany captured(UUID projectId, UUID addedBy, TriageCompanySource source,
                                         TriageCompanyStatus status, CapturedCompanyDetails details) {
        TriageCompany company = new TriageCompany();
        company.projectId = projectId;
        company.addedBy = addedBy;
        company.source = source;
        company.status = status;
        company.companyName = details.companyName();
        company.fileUnder(details.industry());
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

    /** Fills only empty fields: a consultant's capture never loses a value to a vendor. */
    public void enrichFacts(CapturedCompanyDetails details) {
        if (industry == null) {
            fileUnder(details.industry());
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

    /** Replaces the company's own facts; provenance columns are {@code updatable = false}. */
    public void describe(CapturedCompanyDetails details) {
        this.companyName = details.companyName();
        fileUnder(details.industry());
        this.companyCountry = details.companyCountry();
        this.companyCity = details.companyCity();
        this.numEmployees = details.numEmployees();
        this.annualRevenue = details.annualRevenue();
        this.website = details.website();
        this.companyLinkedinUrl = details.companyLinkedinUrl();
        this.foundedYear = details.foundedYear();
        this.shortDescription = details.shortDescription();
    }

    /** The single writer of the industry and its three derived forms, so they always agree. */
    private void fileUnder(String suppliedIndustry) {
        ResolvedIndustry resolved = Industries.resolve(suppliedIndustry);
        this.industry = resolved == null ? null : resolved.label();
        this.industryV2Code = resolved == null ? null : resolved.linkedInCode();
        this.industryV2Label = resolved == null ? null : resolved.v2Label();
        this.sectorGroup = resolved == null ? null : resolved.sectorGroup();
    }

    /**
     * Keyed on the snapshot, not the door: a plugin capture can resolve to a market row badged
     * EXTENSION or MANUAL, and that row is the ETL's, not the mandate's to rewrite.
     */
    public boolean isMandateSupplied() {
        return apolloAccountId == null;
    }

    public void moveTo(TriageCompanyStatus newStatus) {
        this.status = newStatus;
    }

    /** Replaces the whole bag, already merged by {@code CustomColumnService.applyTo}. */
    public void describeCustomFields(CustomFieldValues values) {
        this.customFields = values == null ? CustomFieldValues.empty() : values;
    }

    /** Blank clears the note rather than storing an empty string, so "has a note" stays a null check. */
    public void annotate(String newNote) {
        this.note = newNote == null || newNote.isBlank() ? null : newNote.trim();
    }

    public void flagNoExecutiveFound() {
        this.noExecutiveFound = true;
    }

    public void unflagNoExecutiveFound() {
        this.noExecutiveFound = false;
    }
}
