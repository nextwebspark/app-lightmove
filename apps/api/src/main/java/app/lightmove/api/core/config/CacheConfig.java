package app.lightmove.api.core.config;

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

/**
 * The caches over the company universe, and the only ones the application has. Why they are safe to
 * share process-wide is argued on {@code ApolloCompanyQueryService}; read that before adding one here.
 *
 * <p>{@link SimpleCacheManager} so an unknown cache name fails the call rather than minting one.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * The five filter accordions, each under an explicit literal key. Those keys are load-bearing:
     * all five methods take no arguments, and Spring keys a zero-argument method as
     * {@code SimpleKey.EMPTY}, so without them the five would share one entry and four would be
     * served the fifth's value — no exception, no clue.
     */
    public static final String COMPANY_FACETS = "companyFacets";

    /** The picker typeahead, keyed by the query and the row limit. */
    public static final String COMPANY_TYPEAHEAD = "companyTypeahead";

    /** How many companies a filter matches, keyed by the scope. */
    public static final String COMPANY_SCOPE_COUNT = "companyScopeCount";

    /** One page of a filtered list, keyed by the scope, the sort and the page. */
    public static final String COMPANY_SCOPE_PAGE = "companyScopePage";

    /** Every cache over the universe, so a reload can clear them as one. */
    public static final List<String> COMPANY_UNIVERSE_CACHES =
            List.of(COMPANY_FACETS, COMPANY_TYPEAHEAD, COMPANY_SCOPE_COUNT, COMPANY_SCOPE_PAGE);

    /** Five keys today, with headroom: a sixth added upstream must not silently thrash at exactly 5. */
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

    /** {@code expireAfterWrite}: a hot entry is the one most likely to be wrong after a reload. */
    private static CaffeineCache cache(String name, Duration ttl, int maximumEntries) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maximumEntries)
                .recordStats()
                .build());
    }
}
