package app.lightmove.api.notetaker.service;

import static app.lightmove.api.candidate.service.PersonErasureQueries.count;
import static app.lightmove.api.candidate.service.PersonErasureQueries.scopeOf;

import app.lightmove.api.candidate.constant.ErasedHolding;
import app.lightmove.api.candidate.model.ErasedPerson;
import app.lightmove.api.candidate.service.PersonErasureContributor;
import app.lightmove.api.core.persistence.service.RequiresNewTransactions;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A person's recorded calls. The rows go by cascade; Recall's copy does not, so each bot is queued for
 * {@link RecallBotErasureSweeper} in the erasure's transaction and tried once it commits.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PersonErasureNotetaker implements PersonErasureContributor {

    private final NamedParameterJdbcTemplate jdbc;
    private final RecallBotErasureSweeper sweeper;
    private final RequiresNewTransactions requiresNew;
    private final Clock clock;

    @Override
    public Map<ErasedHolding, Long> holdingsOf(UUID workspaceId, UUID personId) {
        return Map.of(ErasedHolding.RECORDINGS, count(jdbc, """
                SELECT count(*) FROM app_lm_meeting_recording
                WHERE person_id = :personId AND workspace_id = :workspaceId
                """, scopeOf(workspaceId, personId)));
    }

    @Override
    public void erase(ErasedPerson erased) {
        int queued = jdbc.update("""
                INSERT INTO app_lm_recall_bot_erasure (recall_bot_id, call_back, delete_media)
                SELECT recall_bot_id, status IN ('SCHEDULED', 'JOINING', 'RECORDING', 'PROCESSING'), NOT media_deleted
                FROM app_lm_meeting_recording
                WHERE person_id = :personId AND workspace_id = :workspaceId AND recall_bot_id IS NOT NULL
                  AND (status IN ('SCHEDULED', 'JOINING', 'RECORDING', 'PROCESSING') OR NOT media_deleted)
                ON CONFLICT (recall_bot_id) DO NOTHING
                """, scopeOf(erased.workspaceId(), erased.personId()));
        if (queued == 0) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    requiresNew.run(() -> sweeper.sweepAt(clock.instant()));
                } catch (RuntimeException failed) {
                    log.warn("Recall cleanup after an erasure is left to the sweeper: {}", failed.getMessage());
                }
            }
        });
    }
}
