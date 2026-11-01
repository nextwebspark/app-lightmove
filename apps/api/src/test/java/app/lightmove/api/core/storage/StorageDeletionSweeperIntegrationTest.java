package app.lightmove.api.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.storage.service.DocumentStore;
import app.lightmove.api.core.storage.service.StorageDeletionSweeper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** V174's queue: a queued file is deleted, and one the store refuses is kept and tried again later, never dropped. */
@IntegrationTest
class StorageDeletionSweeperIntegrationTest {

    @Autowired @FixtureJdbc JdbcTemplate db;
    @Autowired DocumentStore store;
    @Autowired StorageDeletionSweeper sweeper;

    @Test
    @DisplayName("a queued file goes; a refused one stays queued, backed off, and is not tried again before its time")
    void deletesAndBacksOff() throws IOException {
        String kept = "sweeper-test/" + UUID.randomUUID();
        store.put(kept, new ByteArrayInputStream(new byte[] {1, 2, 3}), 3, "application/octet-stream");
        String refused = "../outside-the-root-" + UUID.randomUUID();
        Instant now = Instant.now();
        db.update("insert into app_lm_storage_deletion (storage_key, next_attempt_at) values (?, ?), (?, ?)",
                kept, Timestamp.from(now), refused, Timestamp.from(now));

        sweeper.sweepAt(now);

        assertThatThrownBy(() -> store.open(kept).close()).isInstanceOf(IOException.class);
        assertThat(db.queryForObject("select count(*) from app_lm_storage_deletion where storage_key = ?", Long.class,
                kept)).isZero();
        Map<String, Object> retry = db.queryForMap("select attempts, next_attempt_at from app_lm_storage_deletion"
                + " where storage_key = ?", refused);
        assertThat(retry.get("attempts")).isEqualTo(1);
        assertThat(((Timestamp) retry.get("next_attempt_at")).toInstant()).isAfter(now.plus(Duration.ofSeconds(59)));

        sweeper.sweepAt(now.plus(Duration.ofSeconds(30)));
        assertThat(db.queryForObject("select attempts from app_lm_storage_deletion where storage_key = ?",
                Integer.class, refused)).isEqualTo(1);
        db.update("delete from app_lm_storage_deletion where storage_key = ?", refused);
    }
}
