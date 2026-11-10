package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.persistence.service.RequiresNewTransactions;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Calls back and strips the media of the Recall bots an erasure or a restriction of processing queued (V197), retrying
 * until Recall answers. Past Recall's own retention there is nothing left to delete, so a bot still refused then is
 * given up, loudly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecallBotErasureSweeper {

    private static final int BATCH = 50;
    private static final Duration LEASE = Duration.ofMinutes(10);
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);
    static final Duration GIVE_UP_AFTER = Duration.ofDays(8);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final RecallBotApi recall;
    private final RequiresNewTransactions requiresNew;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${lightmove.recall.bot-erasure-sweep-interval}",
            initialDelayString = "${lightmove.recall.bot-erasure-sweep-interval}")
    public void scheduledSweep() {
        sweepAt(clock.instant());
    }

    /** Queues bots to call back and strip of media, in the caller's transaction, and tries them once it commits. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void queue(Collection<String> botIds) {
        if (botIds.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("""
                INSERT INTO app_lm_recall_bot_erasure (recall_bot_id, call_back, delete_media) VALUES (?, true, true)
                ON CONFLICT (recall_bot_id) DO NOTHING
                """, botIds.stream().map(botId -> new Object[] {botId}).toList());
        sweepAfterCommit();
    }

    /** Tries the queue once the caller's transaction commits; whatever Recall refuses then is the schedule's. */
    void sweepAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    requiresNew.run(() -> sweepAt(clock.instant()));
                } catch (RuntimeException failed) {
                    log.warn("Recall cleanup is left to the sweeper: {}", failed.getMessage());
                }
            }
        });
    }

    /** Answers how many bots Recall is done with. */
    public int sweepAt(Instant now) {
        int released = 0;
        List<QueuedBot> claimed;
        do {
            claimed = claim(now);
            for (QueuedBot queued : claimed) {
                if (release(queued, now)) {
                    released++;
                }
            }
        } while (claimed.size() == BATCH);
        return released;
    }

    private List<QueuedBot> claim(Instant now) {
        return Objects.requireNonNullElse(transaction.execute(status -> jdbc.query("""
                UPDATE app_lm_recall_bot_erasure SET next_attempt_at = ?
                WHERE id IN (SELECT id FROM app_lm_recall_bot_erasure WHERE next_attempt_at <= ?
                             ORDER BY next_attempt_at LIMIT ? FOR UPDATE SKIP LOCKED)
                RETURNING id, recall_bot_id, call_back, delete_media, attempts, created_at
                """, (row, index) -> new QueuedBot(row.getObject("id", UUID.class), row.getString("recall_bot_id"),
                        row.getBoolean("call_back"), row.getBoolean("delete_media"), row.getInt("attempts"),
                        row.getTimestamp("created_at").toInstant()),
                Timestamp.from(now.plus(LEASE)), Timestamp.from(now), BATCH)), List.of());
    }

    private boolean release(QueuedBot queued, Instant now) {
        boolean calledBack = !queued.callBack();
        try {
            if (queued.callBack()) {
                answered(() -> recall.remove(queued.botId()));
                calledBack = true;
            }
            if (queued.deleteMedia()) {
                answered(() -> recall.deleteMedia(queued.botId()));
            }
            jdbc.update("DELETE FROM app_lm_recall_bot_erasure WHERE id = ?", queued.id());
            return true;
        } catch (RuntimeException refused) {
            int attempts = queued.attempts() + 1;
            if (now.isAfter(queued.queuedAt().plus(GIVE_UP_AFTER))) {
                jdbc.update("DELETE FROM app_lm_recall_bot_erasure WHERE id = ?", queued.id());
                log.error("Recall still refused queued bot {} after {} attempts; check Recall's dashboard: {}",
                        queued.botId(), attempts, refused.getMessage());
                return false;
            }
            jdbc.update("""
                    UPDATE app_lm_recall_bot_erasure SET call_back = ?, attempts = ?, next_attempt_at = ? WHERE id = ?
                    """, !calledBack, attempts, Timestamp.from(now.plus(backoffAfter(attempts))), queued.id());
            log.warn("Recall refused queued bot {}, attempt {}: {}", queued.botId(), attempts, refused.getMessage());
            return false;
        }
    }

    /** A bot or media Recall no longer has is as good as deleted. */
    private static void answered(Runnable call) {
        try {
            call.run();
        } catch (VendorException refused) {
            if (refused.getKind() != VendorFailureKind.NOT_FOUND && refused.getKind() != VendorFailureKind.GONE) {
                throw refused;
            }
        }
    }

    private static Duration backoffAfter(int attempts) {
        Duration backoff = Duration.ofMinutes(1L << Math.min(attempts, 6));
        return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
    }

    private record QueuedBot(UUID id, String botId, boolean callBack, boolean deleteMedia, int attempts,
                             Instant queuedAt) {}
}
