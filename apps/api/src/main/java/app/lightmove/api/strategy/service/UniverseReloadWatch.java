package app.lightmove.api.strategy.service;

import app.lightmove.api.strategy.config.CompanyCacheConfig;
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
 * <p><b>Callers must stay controllers.</b> {@code @Cacheable} short-circuits the method body, so a
 * check moved into {@code ApolloCompanyQueryService} would never run on a hit — disabling this class
 * with every test still green. Nor inside a {@code @Transactional} method: a failed probe would abort
 * the transaction the reads then use.
 */
@Slf4j
@Service
public class UniverseReloadWatch {

    /** Both halves: {@code max(updated_at)} cannot see a reload that only removed rows. */
    private static final String FINGERPRINT_SQL = """
            SELECT count(*) AS company_count, max(updated_at) AS last_loaded_at
            FROM app_lm_apollo_companies
            """;

    private final JdbcClient jdbc;
    private final CacheManager caches;
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

    public void checkForReload() {
        if (!enabled) {
            return;
        }
        long now = System.nanoTime();
        long lastChecked = lastCheckedAt.get();
        if (now - lastChecked < checkIntervalNanos) {
            return;
        }
        if (!lastCheckedAt.compareAndSet(lastChecked, now)) {
            return;
        }
        probe();
    }

    /** A failed probe must not fail the request carrying it. */
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
                CompanyCacheConfig.COMPANY_UNIVERSE_CACHES.size());
        clearCaches();
    }

    private void clearCaches() {
        for (String cacheName : CompanyCacheConfig.COMPANY_UNIVERSE_CACHES) {
            Cache cache = caches.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    /** Clear the caches and the baseline, for when something other than the pipeline replaces rows. */
    public void resetBaseline() {
        clearCaches();
        lastSeen.set(null);
        lastCheckedAt.set(System.nanoTime() - checkIntervalNanos);
    }

    record UniverseFingerprint(long companyCount, Instant lastLoadedAt) {}
}
