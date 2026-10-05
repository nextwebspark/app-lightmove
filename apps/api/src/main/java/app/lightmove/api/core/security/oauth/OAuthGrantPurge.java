package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.config.LightMoveProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes grants nobody can use any more — every token expired, or a request never consented to — and the rotated
 * refresh hashes older than any refresh token could be. A replay of one that old is refused as unknown instead.
 */
@Slf4j
@Component
public class OAuthGrantPurge {

    /** How long a request may wait on the consent screen before it is cleared away. */
    private static final Duration UNCONSENTED_GRACE = Duration.ofHours(1);

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final Duration refreshTokenTtl;

    public OAuthGrantPurge(NamedParameterJdbcTemplate jdbc, Clock clock, LightMoveProperties properties) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.refreshTokenTtl = properties.mcp().refreshTokenTtl();
    }

    @Scheduled(fixedDelayString = "${lightmove.mcp.purge-interval}", initialDelayString = "${lightmove.mcp.purge-interval}")
    public void scheduledPurge() {
        purgeAt(clock.instant());
    }

    @Transactional
    public void purgeAt(Instant now) {
        int grants = jdbc.update("""
                DELETE FROM app_lm_oauth_authorization
                WHERE COALESCE(GREATEST(code_expires_at, access_token_expires_at, refresh_token_expires_at),
                               created_at + make_interval(secs => :graceSeconds)) < :now
                """, new MapSqlParameterSource("now", Timestamp.from(now))
                .addValue("graceSeconds", UNCONSENTED_GRACE.toSeconds()));
        int retired = jdbc.update("DELETE FROM app_lm_oauth_retired_refresh_token WHERE retired_at < :cutoff",
                new MapSqlParameterSource("cutoff", Timestamp.from(now.minus(refreshTokenTtl))));
        if (grants + retired > 0) {
            log.info("Purged {} expired OAuth grants and {} retired refresh token hashes", grants, retired);
        }
    }
}
