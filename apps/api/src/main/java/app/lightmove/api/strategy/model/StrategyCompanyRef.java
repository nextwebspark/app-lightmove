package app.lightmove.api.strategy.model;

import app.lightmove.api.common.location.service.Countries;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One company on a mandate's off-limits list: its identity in the universe, plus a snapshot of how it
 * looked when it was put there.
 *
 * <p>{@code apolloAccountId} is deliberately <b>not</b> a foreign key: {@code app_lm_apollo_companies}
 * is ETL-owned and reloaded wholesale, and an exclusion list must not be something a reload can
 * cascade away. The snapshot is the other half of that — a barred company that renders as a blank row
 * is worse than a stale one, because the list is read to check what is excluded.
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
        ref.industry = row.industry();
        ref.companyCity = Countries.cityOf(row.companyCity());
        ref.companyCountry = Countries.nameOf(row.companyCountry());
        ref.logoUrl = row.logoUrl();
        return ref;
    }
}
