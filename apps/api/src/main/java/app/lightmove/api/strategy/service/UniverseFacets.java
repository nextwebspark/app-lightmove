package app.lightmove.api.strategy.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.strategy.dto.FacetCount;
import app.lightmove.api.strategy.dto.SectorGroup;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.model.ScopeBreakdown;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The whole-universe counts, held in memory for {@code lightmove.company.facet-cache-ttl}. Together
 * they are fifteen full scans of the universe, and no filter or tenant changes them — only the
 * pipeline's reload does.
 */
@Component
public class UniverseFacets {

    private final ApolloCompanyQueryService companies;
    private final Cache<String, Object> counts;

    @Autowired
    public UniverseFacets(ApolloCompanyQueryService companies, LightMoveProperties properties) {
        this(companies, properties.company().facetCacheTtl(), Ticker.systemTicker());
    }

    UniverseFacets(ApolloCompanyQueryService companies, Duration ttl, Ticker ticker) {
        this.companies = companies;
        this.counts = Caffeine.newBuilder().expireAfterWrite(ttl).ticker(ticker).build();
    }

    public List<SectorGroup> sectorGroups() {
        return cached("sectors", companies::sectorGroups);
    }

    public List<FacetCount> marketSegments() {
        return cached("segments", companies::marketSegmentFacets);
    }

    public List<FacetCount> employeeBands() {
        return cached("employees", companies::employeeBandFacets);
    }

    public List<FacetCount> revenueBands() {
        return cached("revenue", companies::revenueBandFacets);
    }

    public List<ScopeBreakdown> countries(int limit) {
        return cached("countries:" + limit,
                () -> companies.countByCountry(CompanyScope.unfiltered(), limit));
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(String key, Supplier<T> read) {
        return (T) counts.get(key, ignored -> read.get());
    }
}
