package app.lightmove.api.strategy.config;

import app.lightmove.api.core.config.CompanyCacheSettings;
import app.lightmove.api.core.config.LightMoveProperties;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The caches over the company universe. Why they may be shared: {@code ApolloCompanyQueryService}. */
@Configuration
@EnableCaching
public class CompanyCacheConfig {

    /**
     * Its five entries need explicit literal keys: the facet methods take no arguments, and Spring
     * keys a zero-argument method as {@code SimpleKey.EMPTY}, so without them all five share one entry
     * and four are served the fifth's value, silently.
     */
    public static final String COMPANY_FACETS = "companyFacets";

    public static final String COMPANY_TYPEAHEAD = "companyTypeahead";
    public static final String COMPANY_SCOPE_COUNT = "companyScopeCount";
    public static final String COMPANY_SCOPE_PAGE = "companyScopePage";

    public static final List<String> COMPANY_UNIVERSE_CACHES =
            List.of(COMPANY_FACETS, COMPANY_TYPEAHEAD, COMPANY_SCOPE_COUNT, COMPANY_SCOPE_PAGE);

    /** Headroom: five keys today, and a sixth must not silently thrash at a maximum of exactly five. */
    private static final int FACET_ENTRIES = 16;

    @Bean
    public CacheManager cacheManager(LightMoveProperties properties) {
        CompanyCacheSettings config = properties.company().cache();
        if (!config.enabled()) {
            return new NoOpCacheManager();
        }

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                cache(COMPANY_FACETS, config.facetsTtl(), FACET_ENTRIES),
                cache(COMPANY_TYPEAHEAD, config.typeaheadTtl(), config.typeaheadMaxEntries()),
                cache(COMPANY_SCOPE_COUNT, config.scopeTtl(), config.scopeCountMaxEntries()),
                cache(COMPANY_SCOPE_PAGE, config.scopeTtl(), config.scopePageMaxEntries())));
        return manager;
    }

    private static CaffeineCache cache(String name, Duration ttl, int maximumEntries) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maximumEntries)
                .recordStats()
                .build());
    }
}
