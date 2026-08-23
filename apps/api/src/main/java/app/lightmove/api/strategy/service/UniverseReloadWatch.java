package app.lightmove.api.strategy.service;

import app.lightmove.api.core.config.CacheConfig;
import app.lightmove.api.core.config.CompanyCacheSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Notices that the pipeline has reloaded the company universe, and clears every cache over it.
 *
 * <p>The caches in {@link CacheConfig} hold reads that only one event invalidates: the ETL rewriting
 * {@code app_lm_apollo_companies}, by hand and on no schedule this application can know. A TTL alone
 * would serve counts known to be wrong for whatever is left on it.
 *
 * <p>Not {@code @Scheduled}: Cloud Run throttles CPU to near-zero between requests here
 * ({@code --min-instances 0}, no {@code --no-cpu-throttling} in {@code ops/gcp/deploy.sh}).
 *
 * <p><b>Callers are controllers, and must stay controllers.</b> {@code @Cacheable} short-circuits the
 * method body, so a check moved inside {@code ApolloCompanyQueryService} would never run on a cache
 * hit — silently disabling this class while every test stayed green. It also must not run inside a
 * {@code @Transactional} method: a failed probe would abort the transaction the reads then use.
 */
@Slf4j
@Service
public class UniverseReloadWatch {

    /**
     * Both halves: {@code max(updated_at)} cannot see a reload that only removed rows, {@code count(*)}
     * can. Snake-case aliases to match the table's own column naming, and because the mapper resolves
     * them directly rather than through its lower-case-first fallback.
     */
    private static final String FINGERPRINT_SQL = """
            SELECT count(*) AS company_count, max(updated_at) AS last_loaded_at
            FROM app_lm_apollo_companies
            """;

    private final JdbcClient jdbc;
    private final CacheManager caches;
    /** Off means there is nothing to invalidate, so the probe is pure cost. */
    private final boolean enabled;
    private final long checkIntervalNanos;

    /** Null until the first probe, which therefore only records — "never looked" is not a change. */
    private final AtomicReference<UniverseFingerprint> lastSeen = new AtomicReference<>();

    /** {@code nanoTime}: a wall clock stepping backwards would suspend detection for the step. */
    private final AtomicLong lastCheckedAt;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public UniverseReloadWatch(JdbcClient jdbc, CacheManager caches, LightMoveProperties properties) {
        this.jdbc = jdbc;
        this.caches = caches;
        CompanyCacheSettings config = properties.company().cache();
        this.enabled = config.enabled();
        this.checkIntervalNanos = config.reloadCheckInterval().toNanos();
        this.lastCheckedAt = new AtomicLong(System.nanoTime() - checkIntervalNanos);
    }

    /** Called before serving a universe read. Returns at once unless this caller owes the check. */
    public void checkForReload() {
        if (!enabled) {
            return;
        }
        long now = System.nanoTime();
        long lastChecked = lastCheckedAt.get();
        if (now - lastChecked < checkIntervalNanos) {
            return;
        }
        // Claim the slot before probing, not after: two callers arriving together must produce one
        // query, and the loser must not wait for the winner to finish to find that out.
        if (!lastCheckedAt.compareAndSet(lastChecked, now)) {
            return;
        }
        probe();
    }

    /**
     * A failed probe must not fail the request carrying it: the worst case of an unreadable
     * fingerprint is a cache one TTL stale, which is the behaviour without this class at all.
     *
     * <p>One window stays open, deliberately. A read that missed and is still running when the clear
     * happens will write its pre-reload value in afterwards, and the fingerprint has already advanced
     * so no later probe will clear it — that entry lives out its TTL. Closing it needs a generation
     * counter and hand-rolled cache access; the TTL is the backstop until that is worth the code.
     */
    private void probe() {
        UniverseFingerprint current;
        try {
            current = jdbc.sql(FINGERPRINT_SQL).query(UniverseFingerprint.class).single();
        } catch (RuntimeException failure) {
            log.warn("Could not read the company universe fingerprint; caches keep their TTL", failure);
            return;
        }

        UniverseFingerprint previous = lastSeen.getAndSet(current);
        if (previous == null || previous.equals(current)) {
            return;
        }
        log.info("Company universe reloaded ({} companies at {}, was {} at {}) — clearing {} caches",
                current.companyCount(), current.lastLoadedAt(),
                previous.companyCount(), previous.lastLoadedAt(),
                CacheConfig.COMPANY_UNIVERSE_CACHES.size());
        clearCaches();
    }

    /** Clear every cache over the universe, keeping the baseline of which universe it held. */
    private void clearCaches() {
        for (String cacheName : CacheConfig.COMPANY_UNIVERSE_CACHES) {
            Cache cache = caches.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    /**
     * Clear the caches <i>and</i> the baseline, for when something other than the pipeline replaces
     * the rows — today, only a test seeding its own. Not synchronised against a concurrent probe,
     * which could restore the baseline it just dropped; its callers are single-threaded test setup.
     */
    public void resetBaseline() {
        clearCaches();
        lastSeen.set(null);
        lastCheckedAt.set(System.nanoTime() - checkIntervalNanos);
    }

    /**
     * What the universe looked like the last time anyone asked.
     *
     * <p>{@code lastLoadedAt} is null on an empty universe, which record equality handles.
     */
    record UniverseFingerprint(long companyCount, Instant lastLoadedAt) {}
}
