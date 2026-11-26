package app.lightmove.api.candidate.service;

import app.lightmove.api.core.config.LightMoveProperties;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which version of Uncava's own notice to executives (#1490, B2/B6) a person was last sent, and when: written only
 * once an email carrying its link was delivered, so a refused email records nothing. The columns are not mapped on
 * {@code Person}, so an entity flush never writes them back.
 */
@Service
@RequiredArgsConstructor
public class UncavaNoticeRecords {

    private final NamedParameterJdbcTemplate jdbc;
    private final LightMoveProperties properties;

    /** The version every candidate email carries now; null while no notice is published. */
    public String versionCarried() {
        return properties.legal().candidateNoticeVersion();
    }

    /** The latest delivery wins; an older one arriving late never winds the record back. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDelivered(UUID workspaceId, Collection<UUID> personIds, Instant deliveredAt) {
        String version = versionCarried();
        if (version == null || personIds.isEmpty()) {
            return;
        }
        jdbc.update("""
                UPDATE app_lm_person SET uncava_notice_version = :version, uncava_notice_sent_at = :at
                WHERE workspace_id = :workspaceId AND id IN (:personIds)
                  AND (uncava_notice_sent_at IS NULL OR uncava_notice_sent_at <= :at)
                """, new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("personIds", personIds)
                .addValue("version", version)
                .addValue("at", Timestamp.from(deliveredAt)));
    }
}
