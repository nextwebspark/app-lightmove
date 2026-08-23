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
 * The caches over the company universe, and the only ones the application has.
 *
 * <p>Every entry here holds a read over {@code app_lm_apollo_companies} — 71,822 rows of ETL-owned
 * reference data the application never writes and every workspace reads identically. That is what
 * makes these safe to share process-wide: no cached value is derived from a workspace, a project or a
 * user, so there is no tenant to leak across. {@code ApolloCompanyQueryService} carries the argument
 * in full on the methods it applies to; read it before adding a cache here.
 *
 * <p><b>{@link SimpleCacheManager}, not {@code CaffeineCacheManager}.</b> The latter mints a cache on
 * demand for any name it is asked for, so a typo in a {@code @Cacheable} name yields a silent second
 * cache with default settings — unbounded, never expiring — that nothing ever clears. Registering the
 * four explicitly means an unknown name has no cache at all, and Spring fails the call rather than
 * quietly building the wrong thing.
 *
 * <p>Stats recording is on so Boot's {@code CacheMetricsAutoConfiguration} binds hit and miss counters
 * onto the Prometheus registry that is already wired up. A cache nobody can measure is a cache nobody
 * can tell is working.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * The five filter accordions. One cache, five entries, each under an explicit literal key.
     *
     * <p>The literal keys are load-bearing, not decoration. All five facet methods take <b>no
     * arguments</b>, and Spring's default key generator answers a zero-argument method with
     * {@code SimpleKey.EMPTY} — the same key for all five. Sharing this cache without naming a key
     * would give whichever method ran first the single entry, and the other four would be served its
     * value: {@code sectorGroups()} returning country facets, with no exception and no clue.
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

    /**
     * One entry per facet method: sectors, market segments, countries, employee bands, revenue bands.
     * A fixed five, so the cache cannot grow whatever else changes.
     */
    private static final int FACET_ENTRIES = 5;

    @Bean
    public CacheManager cacheManager(LightMoveProperties properties) {
        CompanyCacheSettings config = properties.company().cache();
        // A no-op manager rather than skipping @EnableCaching: the annotations stay in place and
        // inert, so switching this off is a configuration change and not a different code path.
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

    /**
     * {@code expireAfterWrite}, never {@code expireAfterAccess}: the TTL is a bound on how stale an
     * answer may be, and an entry that stays fresh by being read often is exactly the entry most
     * likely to be wrong after a reload.
     */
    private static CaffeineCache cache(String name, Duration ttl, int maximumEntries) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maximumEntries)
                .recordStats()
                .build());
    }
}
