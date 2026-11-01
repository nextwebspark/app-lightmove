package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.tenant.EveryWorkspace;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes grants nobody can use any more — every token expired, or a request never consented to — and the rotated
 * refresh hashes older than any refresh token could be. A replay of one that old is refused as unknown instead. Then
 * registered or document clients nobody has connected for a while, and never one with a grant.
 */
@Slf4j
@Component
public class OAuthGrantPurge {

    /** How long a request may wait on the consent screen before it is cleared away. */
    private static final Duration UNCONSENTED_GRACE = Duration.ofHours(1);

    private final NamedParameterJdbcTemplate jdbc;
    private final EveryWorkspace workspaces;
    private final Clock clock;
    private final Duration refreshTokenTtl;
    private final Duration unusedClientTtl;

    public OAuthGrantPurge(NamedParameterJdbcTemplate jdbc, EveryWorkspace workspaces, Clock clock,
                           LightMoveProperties properties) {
        this.jdbc = jdbc;
        this.workspaces = workspaces;
        this.clock = clock;
        this.refreshTokenTtl = properties.mcp().refreshTokenTtl();
        this.unusedClientTtl = properties.mcp().unusedClientTtl();
    }

    @Scheduled(fixedDelayString = "${lightmove.mcp.purge-interval}", initialDelayString = "${lightmove.mcp.purge-interval}")
    public void scheduledPurge() {
        purgeAt(clock.instant());
    }

    /**
     * A workspace at a time: the grants are under row-level security. A grant waiting for its consent belongs to none
     * and is cleared on the first workspace's pass.
     */
    public void purgeAt(Instant now) {
        MapSqlParameterSource expired = new MapSqlParameterSource("now", Timestamp.from(now))
                .addValue("graceSeconds", UNCONSENTED_GRACE.toSeconds());
        MapSqlParameterSource retiredBefore = new MapSqlParameterSource("cutoff",
                Timestamp.from(now.minus(refreshTokenTtl)));
        Set<UUID> clientsHeld = new HashSet<>();
        int[] retired = {0};
        int grants = workspaces.sum(workspaceId -> {
            int deleted = jdbc.update("""
                    DELETE FROM app_lm_oauth_authorization
                    WHERE COALESCE(GREATEST(code_expires_at, access_token_expires_at, refresh_token_expires_at),
                                   created_at + make_interval(secs => :graceSeconds)) < :now
                    """, expired);
            retired[0] += jdbc.update("DELETE FROM app_lm_oauth_retired_refresh_token WHERE retired_at < :cutoff",
                    retiredBefore);
            clientsHeld.addAll(jdbc.queryForList("SELECT DISTINCT client_id FROM app_lm_oauth_authorization",
                    new MapSqlParameterSource(), UUID.class));
            return deleted;
        });
        // Never a client with a grant left, in any workspace: the cascade would take a live connection with it. Read
        // with no workspace, no grant would be seen and every old client would go.
        int unusedClients = jdbc.update("""
                DELETE FROM app_lm_oauth_client c
                WHERE c.source IN ('DCR', 'CIMD')
                  AND COALESCE(c.last_authorized_at, c.created_at) < :cutoff
                  AND (c.source = 'DCR' OR c.metadata_expires_at < :cutoff)
                  AND c.id <> ALL (CAST(:held AS uuid[]))
                  AND NOT EXISTS (SELECT 1 FROM app_lm_oauth_authorization g WHERE g.client_id = c.id)
                """, new MapSqlParameterSource("cutoff", Timestamp.from(now.minus(unusedClientTtl)))
                .addValue("held", clientsHeld.stream().map(UUID::toString)
                        .collect(Collectors.joining(",", "{", "}"))));
        if (grants + retired[0] + unusedClients > 0) {
            log.info("Purged {} expired OAuth grants, {} retired refresh token hashes and {} unused clients",
                    grants, retired[0], unusedClients);
        }
    }
}
