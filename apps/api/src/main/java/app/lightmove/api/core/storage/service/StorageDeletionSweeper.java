package app.lightmove.api.core.storage.service;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deletes from the bucket the files {@link StorageDeletionQueue} was handed. A batch is claimed by pushing its next
 * attempt past a lease in a committed transaction, so two instances never work one key; a delete the bucket refuses is
 * tried again later, backing off to a day, and never given up — a file whose rows are gone must not outlive them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageDeletionSweeper {

    private static final int BATCH = 200;
    private static final Duration LEASE = Duration.ofMinutes(10);
    private static final Duration MAX_BACKOFF = Duration.ofDays(1);
    private static final int ALARM_AFTER_ATTEMPTS = 10;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final DocumentStore store;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${lightmove.storage.deletion-sweep-interval}",
            initialDelayString = "${lightmove.storage.deletion-sweep-interval}")
    public void scheduledSweep() {
        sweepAt(clock.instant());
    }

    /** Answers how many files went. */
    public int sweepAt(Instant now) {
        int deleted = 0;
        List<QueuedKey> claimed;
        do {
            claimed = claim(now);
            for (QueuedKey queued : claimed) {
                if (deleteOne(queued, now)) {
                    deleted++;
                }
            }
        } while (claimed.size() == BATCH);
        return deleted;
    }

    private List<QueuedKey> claim(Instant now) {
        return transaction.execute(status -> jdbc.query("""
                UPDATE app_lm_storage_deletion SET next_attempt_at = ?
                WHERE id IN (SELECT id FROM app_lm_storage_deletion WHERE next_attempt_at <= ?
                             ORDER BY next_attempt_at LIMIT ? FOR UPDATE SKIP LOCKED)
                RETURNING id, storage_key, attempts
                """, (row, index) -> new QueuedKey(row.getObject("id", UUID.class), row.getString("storage_key"),
                        row.getInt("attempts")),
                Timestamp.from(now.plus(LEASE)), Timestamp.from(now), BATCH));
    }

    private boolean deleteOne(QueuedKey queued, Instant now) {
        try {
            store.delete(queued.storageKey());
            jdbc.update("DELETE FROM app_lm_storage_deletion WHERE id = ?", queued.id());
            return true;
        } catch (IOException | RuntimeException refused) {
            int attempts = queued.attempts() + 1;
            jdbc.update("UPDATE app_lm_storage_deletion SET attempts = ?, next_attempt_at = ? WHERE id = ?",
                    attempts, Timestamp.from(now.plus(backoffAfter(attempts))), queued.id());
            if (attempts >= ALARM_AFTER_ATTEMPTS) {
                log.error("A file whose rows are gone could not be deleted from the bucket after {} attempts: {}",
                        attempts, refused.getMessage());
            } else {
                log.warn("Deleting a file from the bucket failed, attempt {}: {}", attempts, refused.getMessage());
            }
            return false;
        }
    }

    private static Duration backoffAfter(int attempts) {
        Duration backoff = Duration.ofMinutes(1L << Math.min(attempts, 11));
        return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
    }

    private record QueuedKey(UUID id, String storageKey, int attempts) {}
}
