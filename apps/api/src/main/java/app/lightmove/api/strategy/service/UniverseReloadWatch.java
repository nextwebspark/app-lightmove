package app.lightmove.api.strategy.service;

import app.lightmove.api.core.config.CacheConfig;
import app.lightmove.api.core.config.LightMoveProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Notices that the pipeline has reloaded the company universe, and clears every cache over it.
 *
 * <p>The caches in {@link CacheConfig} hold reads that only one event invalidates: the ETL rewriting
 * {@code app_lm_apollo_companies}. That happens by hand, on no schedule anyone here can know, so a TTL
 * alone means serving counts that are known to be wrong for however long is left on it. The table
 * carries {@code updated_at} with {@code idx_lm_apollo_updated} on it, so the universe has a
 * fingerprint that costs almost nothing to take.
 *
 * <p><b>Not {@code @Scheduled}, deliberately.</b> The service runs on Cloud Run with
 * {@code --min-instances 0} and without {@code --no-cpu-throttling} (see {@code ops/gcp/deploy.sh}),
 * so CPU is throttled to near-zero between requests: a scheduled task would fire late, in bursts, or
 * not at all, and would add a scheduler to an application that has none. Riding the read path instead
 * means the check happens when there is traffic — which is exactly when a stale answer costs anything,
 * and when there is CPU to take it with.
 *
 * <p>At most one request per {@code reload-check-interval} pays for the probe. Whoever wins the
 * {@link AtomicLong#compareAndSet} runs it; everyone else returns immediately on what is already
 * cached. Nothing blocks and nothing is synchronized, because a check arriving one request late is
 * not a defect — the whole mechanism is an optimisation over waiting out the TTL.
 */
@Slf4j
@Component
public class UniverseReloadWatch {

    /**
     * {@code count(*)} as well as {@code max(updated_at)}, because they fail differently. The maximum
     * is index-backed and effectively free, and it moves on any insert or update — but it cannot see a
     * reload that only removed rows, since a deletion leaves no timestamp behind. The count is the
     * scan of the two and the reason this is throttled rather than run per request.
     *
     * <p>Snake-case aliases, not camelCase: Postgres folds an unquoted identifier to lower case, so
     * {@code AS companyCount} arrives as {@code companycount} and the record mapper misses it. The
     * mapper converts {@code company_count} to {@code companyCount} itself.
     */
    private static final String FINGERPRINT_SQL = """
            SELECT count(*) AS company_count, max(updated_at) AS last_loaded_at
            FROM app_lm_apollo_companies
            """;

    private final JdbcClient jdbc;
    private final CacheManager caches;
    private final Duration checkInterval;

    /**
     * Null until the first probe. That first probe therefore only records the universe rather than
     * clearing anything: on a cold start the caches are empty, and treating "I have not looked before"
     * as a change would throw away the warm-up of every instance's first two minutes.
     */
    private final AtomicReference<UniverseFingerprint> lastSeen = new AtomicReference<>();

    private final AtomicLong lastCheckedAtMillis = new AtomicLong(Long.MIN_VALUE);

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public UniverseReloadWatch(JdbcClient jdbc, CacheManager caches, LightMoveProperties properties) {
        this.jdbc = jdbc;
        this.caches = caches;
        this.checkInterval = properties.company().cache().reloadCheckInterval();
    }

    /**
     * Called before serving anything read from the universe. Returns at once unless this caller is the
     * one that owes a check.
     */
    public void checkForReload() {
        long now = System.currentTimeMillis();
        long lastChecked = lastCheckedAtMillis.get();
        if (lastChecked != Long.MIN_VALUE && now - lastChecked < checkInterval.toMillis()) {
            return;
        }
        // Claim the slot before probing, not after: two callers arriving together must produce one
        // query, and the loser must not wait for the winner to finish to find that out.
        if (!lastCheckedAtMillis.compareAndSet(lastChecked, now)) {
            return;
        }
        probe();
    }

    /**
     * A failed probe must never fail the request that carried it. The caller is trying to read the
     * universe, and the worst case of an unreadable fingerprint is answering from a cache that is at
     * most one TTL stale — which is the behaviour without this class at all.
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
        evictAll();
    }

    /** Clear every cache over the universe, keeping what is known about which universe it was. */
    public void evictAll() {
        for (String cacheName : CacheConfig.COMPANY_UNIVERSE_CACHES) {
            Cache cache = caches.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    /**
     * Drop the caches <i>and</i> everything remembered about which universe filled them, so the next
     * read re-baselines rather than comparing against a universe that no longer exists.
     *
     * <p>The distinction from {@link #evictAll()} matters when the universe is replaced by something
     * other than the pipeline — a test seeding its own rows is the case that exists today. Comparing
     * the next fingerprint against the pre-replacement one would report a reload that already had its
     * eviction, and re-arming the interval keeps the throttle from deciding, minutes later and in the
     * middle of unrelated work, that it is owed a check.
     */
    public void forgetUniverse() {
        evictAll();
        lastSeen.set(null);
        lastCheckedAtMillis.set(Long.MIN_VALUE);
    }

    /**
     * What the universe looked like the last time anyone asked.
     *
     * <p>A record so equality is the comparison, and both halves matter: an upsert-only reload moves
     * {@code lastLoadedAt}, a purge moves {@code companyCount}, and a reload that did both moves both.
     *
     * <p>{@code lastLoadedAt} is null on an empty universe, which is what a fresh schema holds — the
     * table is ETL-owned and Flyway creates it empty. Left nullable rather than coalesced to a
     * sentinel: {@code -infinity} is a real timestamptz value whose JDBC mapping varies by driver
     * version, and record equality handles a null on either side without any of that.
     */
    public record UniverseFingerprint(long companyCount, Instant lastLoadedAt) {}
}
