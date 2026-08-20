package app.lightmove.api.triagecompany.model;

import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
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

/**
 * A company a mandate has taken a position on — the row "Add to Universe" writes and the triage
 * screen moves between stages.
 *
 * <p>Strategy searches a universe of 71,822 companies that belongs to no project; this table is the
 * handful a mandate has actually decided something about, and the decision is the row. There is no
 * untriaged state: a company nobody has acted on has no row here at all.
 *
 * <p>The snapshot columns are the same contract as {@link StrategyCompanyRef}'s — a triage decision
 * that loses its subject when the pipeline next loads is worse than a stale one — with one addition:
 * {@code note} is the consultant's own remark about this company <i>for this mandate</i>, which is a
 * different thing from Apollo's {@code short_description}, the same sentence for every mandate.
 *
 * <p>{@code status} is stored as its enum name rather than its wire token, matching the CHECK
 * constraint in V32. The wire token is the client's vocabulary and is free to change; the stored name
 * is the schema's.
 */
@Entity
@Table(name = "app_lm_project_triage_company")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TriageCompany extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "apollo_account_id", nullable = false, updatable = false)
    private String apolloAccountId;

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

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "added_by", nullable = false, updatable = false)
    private UUID addedBy;

    /** A company entering the mandate's universe, snapshotted as it stands in Apollo right now. */
    public static TriageCompany taken(UUID projectId, UUID addedBy, String apolloAccountId,
                                               String companyName, String industry, String companyCountry,
                                               String companyCity, Integer numEmployees, Long annualRevenue,
                                               String website, String logoUrl) {
        TriageCompany company = new TriageCompany();
        company.projectId = projectId;
        company.addedBy = addedBy;
        company.apolloAccountId = apolloAccountId;
        company.status = TriageCompanyStatus.IN_UNIVERSE;
        company.companyName = companyName;
        company.industry = industry;
        company.companyCountry = companyCountry;
        company.companyCity = companyCity;
        company.numEmployees = numEmployees;
        company.annualRevenue = annualRevenue;
        company.website = website;
        company.logoUrl = logoUrl;
        return company;
    }

    public void moveTo(TriageCompanyStatus newStatus) {
        this.status = newStatus;
    }

    /** Blank clears the note rather than storing an empty string, so "has a note" stays a null check. */
    public void annotate(String newNote) {
        this.note = newNote == null || newNote.isBlank() ? null : newNote.trim();
    }
}
