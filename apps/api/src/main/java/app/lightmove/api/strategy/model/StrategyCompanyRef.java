package app.lightmove.api.strategy.model;

import app.lightmove.api.common.industry.model.ResolvedIndustry;
import app.lightmove.api.common.industry.service.Industries;
import app.lightmove.api.common.location.service.Countries;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One off-limits company: its universe id — not a foreign key, since the ETL reloads the universe
 * wholesale — plus a write-time snapshot so a barred company never renders blank.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StrategyCompanyRef {

    @Column(name = "apollo_account_id", nullable = false)
    private String apolloAccountId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "industry")
    private String industry;

    /** Derived from {@link #industry} and written with it, never separately. */
    @Column(name = "industry_v2_code")
    private Integer industryV2Code;

    @Column(name = "industry_v2_label")
    private String industryV2Label;

    @Column(name = "sector_group")
    private String sectorGroup;

    @Column(name = "company_city")
    private String companyCity;

    @Column(name = "company_country")
    private String companyCountry;

    @Column(name = "logo_url")
    private String logoUrl;

    public static StrategyCompanyRef of(CompanyRow row) {
        StrategyCompanyRef ref = new StrategyCompanyRef();
        ref.apolloAccountId = row.apolloAccountId();
        ref.companyName = row.companyName();
        ResolvedIndustry industry = Industries.resolve(row.industry());
        ref.industry = industry == null ? null : industry.label();
        ref.industryV2Code = industry == null ? null : industry.linkedInCode();
        ref.industryV2Label = industry == null ? null : industry.v2Label();
        ref.sectorGroup = industry == null ? null : industry.sectorGroup();
        ref.companyCity = Countries.cityOf(row.companyCity());
        ref.companyCountry = Countries.nameOf(row.companyCountry());
        ref.logoUrl = row.logoUrl();
        return ref;
    }
}
