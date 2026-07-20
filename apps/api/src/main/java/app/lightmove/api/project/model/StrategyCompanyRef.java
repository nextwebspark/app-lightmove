package app.lightmove.api.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One company on a strategy list — a target seed or an off-limits entry. Identified by
 * {@code (source, sourceId)}, the rebuild-stable key of {@code app_lm_companies} (its {@code id} is
 * re-minted on every ETL rebuild, so it is never stored). Everything else is a write-time snapshot
 * resolved server-side from the universe, so the list still renders after its company vanishes
 * upstream. Shared by both lists; each holds its own ordered collection on {@link Strategy}.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StrategyCompanyRef {

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "domain")
    private String domain;

    @Column(name = "slogan")
    private String slogan;

    @Column(name = "logo")
    private String logo;

    @Column(name = "hq_city")
    private String hqCity;

    @Column(name = "hq_country")
    private String hqCountry;

    public static StrategyCompanyRef of(String source, String sourceId, String name, String domain,
                                        String slogan, String logo, String hqCity, String hqCountry) {
        StrategyCompanyRef ref = new StrategyCompanyRef();
        ref.source = source;
        ref.sourceId = sourceId;
        ref.name = name;
        ref.domain = domain;
        ref.slogan = slogan;
        ref.logo = logo;
        ref.hqCity = hqCity;
        ref.hqCountry = hqCountry;
        return ref;
    }
}
