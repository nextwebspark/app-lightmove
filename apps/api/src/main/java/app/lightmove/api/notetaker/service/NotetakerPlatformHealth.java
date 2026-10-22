package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * What the daily canary last found on each platform (#956, V156). A failed canary is logged at ERROR — the alert —
 * and, while {@code lightmove.notetaker.platform-health} is on, the platform's calls go to the backup recorder in
 * every workspace that turned it on, until a canary passes again. A platform no canary has reported on is healthy.
 */
@Component
@Slf4j
public class NotetakerPlatformHealth {

    static final String CANARY = "notetaker.canary";

    private final JdbcTemplate jdbc;
    private final LightMoveProperties properties;
    private final MeterRegistry meters;
    private final Clock clock;
    private final LoadingCache<NotetakerPlatform, Boolean> unhealthy;

    NotetakerPlatformHealth(JdbcTemplate jdbc, LightMoveProperties properties, MeterRegistry meters, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.meters = meters;
        this.clock = clock;
        this.unhealthy = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).build(this::readUnhealthy);
    }

    public void record(NotetakerPlatform platform, boolean healthy, String reason) {
        Instant now = clock.instant();
        String why = healthy ? null : reason == null || reason.isBlank() ? "UNKNOWN" : reason.strip();
        jdbc.update("""
                INSERT INTO app_lm_notetaker_platform_health (platform, healthy, reason, checked_at, failing_since)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (platform) DO UPDATE SET
                    healthy = excluded.healthy, reason = excluded.reason, checked_at = excluded.checked_at,
                    failing_since = CASE WHEN excluded.healthy THEN NULL
                                         ELSE coalesce(app_lm_notetaker_platform_health.failing_since,
                                                       excluded.failing_since) END""",
                platform.name(), healthy, why, Timestamp.from(now), healthy ? null : Timestamp.from(now));
        unhealthy.invalidate(platform);
        meters.counter(CANARY, "platform", platform.name(), "outcome", healthy ? "PASSED" : "FAILED").increment();
        if (healthy) {
            log.info("Notetaker canary passed on {}", platform);
        } else {
            log.error("Notetaker canary FAILED on {}: {}; its calls go to the backup recorder where it is on",
                    platform, why);
        }
    }

    /** Whether our bot is to be stepped around on this platform's calls. */
    boolean routesAround(NotetakerPlatform platform) {
        return platform != null && properties.notetaker().platformHealth()
                && Boolean.TRUE.equals(unhealthy.get(platform));
    }

    private Boolean readUnhealthy(NotetakerPlatform platform) {
        return jdbc.query("SELECT NOT healthy FROM app_lm_notetaker_platform_health WHERE platform = ?",
                (row, index) -> row.getBoolean(1), platform.name()).stream().findFirst().orElse(false);
    }
}
