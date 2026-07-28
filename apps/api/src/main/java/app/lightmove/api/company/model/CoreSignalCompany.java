package app.lightmove.api.company.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A CoreSignal company the application has already paid to collect — one row per
 * {@code coresignal_id}, ever. This cache is the POC's single credit safeguard: membership here is
 * what lets a repeat sourcing run, by any project, skip the collect call entirely. Shared across
 * workspaces on purpose (a CoreSignal record is the same facts for every tenant), which is why no
 * {@code workspace_id} exists — the same cross-tenant stance as {@code app_lm_companies}, except
 * this table is app-written because the application is the thing doing the collecting.
 */
@Entity
@Table(name = "app_lm_coresignal_company")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoreSignalCompany extends BaseEntity {

    @Column(name = "coresignal_id", nullable = false, unique = true)
    private long coresignalId;

    @Column(nullable = false)
    private String name;

    @Column
    private String website;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column
    private String description;

    @Column
    private String industry;

    @Column(name = "employees_count")
    private Integer employeesCount;

    @Column(name = "size_range")
    private String sizeRange;

    @Column(name = "revenue_annual")
    private BigDecimal revenueAnnual;

    @Column(name = "revenue_range")
    private String revenueRange;

    @Column(name = "hq_location")
    private String hqLocation;

    @Column(name = "hq_country")
    private String hqCountry;

    @Column(name = "hq_country_iso2")
    private String hqCountryIso2;

    @Column(name = "founded_year")
    private Integer foundedYear;

    @Column(name = "logo_url")
    private String logoUrl;

    /** The collect response verbatim — re-extracting from here is free, re-collecting costs credits. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    public static CoreSignalCompany from(CoreSignalCompanyRecord record) {
        CoreSignalCompany company = new CoreSignalCompany();
        company.coresignalId = record.coresignalId();
        company.refreshFrom(record);
        return company;
    }

    /** Overwrites every collected fact with a fresh collect's — the id is the one thing that never moves. */
    public void refreshFrom(CoreSignalCompanyRecord record) {
        this.name = record.name();
        this.website = record.website();
        this.linkedinUrl = record.linkedinUrl();
        this.description = record.description();
        this.industry = record.industry();
        this.employeesCount = record.employeesCount();
        this.sizeRange = record.sizeRange();
        this.revenueAnnual = record.revenueAnnual();
        this.revenueRange = record.revenueRange();
        this.hqLocation = record.hqLocation();
        this.hqCountry = record.hqCountry();
        this.hqCountryIso2 = record.hqCountryIso2();
        this.foundedYear = record.foundedYear();
        this.logoUrl = record.logoUrl();
        this.payload = record.rawPayload();
    }
}
