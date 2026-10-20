package app.lightmove.api.triagecompany.service;

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

/**
 * Deletes for good the company notes deleted longer ago than {@code lightmove.notes.purge-after}, as
 * {@code PersonNotePurge} does a person's. A plain delete is harmless run twice, so instances need no claim.
 */
@Slf4j
@Component
public class CompanyNotePurge {

    private static final int BATCH = 1000;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final Duration purgeAfter;

    public CompanyNotePurge(NamedParameterJdbcTemplate jdbc, Clock clock, LightMoveProperties properties) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.purgeAfter = properties.notes().purgeAfter();
    }

    @Scheduled(fixedDelayString = "${lightmove.notes.purge-interval}",
            initialDelayString = "${lightmove.notes.purge-interval}")
    public void scheduledPurge() {
        purgeAt(clock.instant());
    }

    public int purgeAt(Instant now) {
        MapSqlParameterSource cutoff = new MapSqlParameterSource("cutoff", Timestamp.from(now.minus(purgeAfter)))
                .addValue("batch", BATCH);
        int purged = 0;
        int deleted;
        do {
            deleted = jdbc.update("""
                    DELETE FROM app_lm_company_note
                    WHERE id IN (SELECT id FROM app_lm_company_note
                                 WHERE deleted_at < :cutoff ORDER BY deleted_at LIMIT :batch)
                    """, cutoff);
            purged += deleted;
        } while (deleted == BATCH);
        if (purged > 0) {
            log.info("Purged {} company notes deleted before {}", purged, now.minus(purgeAfter));
        }
        return purged;
    }
}
