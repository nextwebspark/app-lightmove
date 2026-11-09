package app.lightmove.api.notetaker;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingRecallBotApi;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.notetaker.service.RecallBotErasureSweeper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** V197's queue: an erased person's Recall bot is retried until Recall answers, and given up only past its retention. */
@IntegrationTest
class RecallBotErasureSweeperIntegrationTest {

    @Autowired @FixtureJdbc JdbcTemplate db;
    @Autowired RecallBotErasureSweeper sweeper;
    @Autowired RecordingRecallBotApi recall;

    @BeforeEach
    @AfterEach
    void reset() {
        recall.clear();
        db.update("delete from app_lm_recall_bot_erasure");
    }

    @Test
    @DisplayName("a refused media delete keeps the bot queued, backed off and not called back twice, until Recall takes it")
    void retriesUntilRecallAnswers() {
        String botId = "erased-" + UUID.randomUUID();
        Instant now = Instant.now();
        queue(botId, now, now);
        recall.refuseMediaDeletes(VendorFailureKind.UNAVAILABLE);

        assertThat(sweeper.sweepAt(now)).isZero();

        assertThat(recall.removed()).containsExactly(botId);
        Map<String, Object> retry = db.queryForMap(
                "select call_back, attempts, next_attempt_at from app_lm_recall_bot_erasure where recall_bot_id = ?",
                botId);
        assertThat(retry.get("call_back")).isEqualTo(false);
        assertThat(retry.get("attempts")).isEqualTo(1);
        assertThat(((Timestamp) retry.get("next_attempt_at")).toInstant()).isAfter(now.plus(Duration.ofSeconds(59)));
        assertThat(sweeper.sweepAt(now.plus(Duration.ofSeconds(30)))).isZero();

        recall.refuseMediaDeletes(null);
        assertThat(sweeper.sweepAt(now.plus(Duration.ofMinutes(5)))).isEqualTo(1);

        assertThat(recall.removed()).containsExactly(botId);
        assertThat(recall.mediaDeleted()).containsExactly(botId);
        assertThat(queued(botId)).isZero();
    }

    @Test
    @DisplayName("media Recall no longer holds counts as deleted, and a bot still refused past Recall's retention is let go")
    void goneIsDoneAndRetentionEndsTheRetries() {
        Instant now = Instant.now();
        String gone = "gone-" + UUID.randomUUID();
        queue(gone, now, now);
        recall.refuseMediaDeletes(VendorFailureKind.NOT_FOUND);
        assertThat(sweeper.sweepAt(now)).isEqualTo(1);
        assertThat(queued(gone)).isZero();

        String stale = "stale-" + UUID.randomUUID();
        queue(stale, now.minus(Duration.ofDays(9)), now);
        recall.refuseMediaDeletes(VendorFailureKind.UNAVAILABLE);
        assertThat(sweeper.sweepAt(now)).isZero();
        assertThat(queued(stale)).isZero();
    }

    private void queue(String botId, Instant queuedAt, Instant due) {
        db.update("""
                insert into app_lm_recall_bot_erasure (recall_bot_id, call_back, delete_media, created_at, next_attempt_at)
                values (?, true, true, ?, ?)
                """, botId, Timestamp.from(queuedAt), Timestamp.from(due));
    }

    private long queued(String botId) {
        return db.queryForObject("select count(*) from app_lm_recall_bot_erasure where recall_bot_id = ?", Long.class,
                botId);
    }
}
