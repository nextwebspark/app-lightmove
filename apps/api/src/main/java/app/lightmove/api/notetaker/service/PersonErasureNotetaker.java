package app.lightmove.api.notetaker.service;

import app.lightmove.api.candidate.constant.ErasedHolding;
import app.lightmove.api.candidate.model.ErasedPerson;
import app.lightmove.api.candidate.service.PersonErasureContributor;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A person's recorded calls, as their erasure finds them. The recordings, transcripts and live lines go by cascade;
 * Recall's own copy does not, so once the erasure commits every bot of theirs is called back, out of a call still
 * running, and its media deleted — the 7-day retention would otherwise keep their voice that long.
 */
@Component
@RequiredArgsConstructor
class PersonErasureNotetaker implements PersonErasureContributor {

    private final NamedParameterJdbcTemplate jdbc;
    private final NotetakerService notetaker;

    @Override
    public Map<ErasedHolding, Long> holdingsOf(UUID workspaceId, UUID personId) {
        Long recordings = jdbc.queryForObject("""
                SELECT count(*) FROM app_lm_meeting_recording WHERE person_id = :personId AND workspace_id = :workspaceId
                """, parameters(workspaceId, personId), Long.class);
        return Map.of(ErasedHolding.RECORDINGS, recordings == null ? 0 : recordings);
    }

    @Override
    public void erase(ErasedPerson erased) {
        List<RecallBot> bots = jdbc.query("""
                SELECT recall_bot_id, status IN ('SCHEDULED', 'JOINING', 'RECORDING', 'PROCESSING') AS live,
                       NOT media_deleted AS media_held
                FROM app_lm_meeting_recording
                WHERE person_id = :personId AND workspace_id = :workspaceId AND recall_bot_id IS NOT NULL
                """, parameters(erased.workspaceId(), erased.personId()),
                (row, index) -> new RecallBot(row.getString("recall_bot_id"), row.getBoolean("live"),
                        row.getBoolean("media_held")));
        if (bots.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                bots.forEach(bot -> {
                    if (bot.live()) {
                        notetaker.removeQuietly(bot.botId());
                    }
                    if (bot.mediaHeld()) {
                        notetaker.deleteMediaQuietly(bot.botId());
                    }
                });
            }
        });
    }

    private static MapSqlParameterSource parameters(UUID workspaceId, UUID personId) {
        return new MapSqlParameterSource("personId", personId).addValue("workspaceId", workspaceId);
    }

    private record RecallBot(String botId, boolean live, boolean mediaHeld) {}
}
