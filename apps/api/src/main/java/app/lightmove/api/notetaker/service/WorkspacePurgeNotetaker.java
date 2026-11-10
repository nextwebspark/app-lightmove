package app.lightmove.api.notetaker.service;

import app.lightmove.api.workspace.service.WorkspacePurgeContributor;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Before a deleted workspace's recordings go, Recall's copies go: any bot somehow still live is called back, and the
 * media of every call still held there is deleted. The rows are the only record of which bots were the workspace's,
 * so each is queued for {@link RecallBotErasureSweeper}, which outlives them and retries until Recall answers.
 */
@Component
@RequiredArgsConstructor
class WorkspacePurgeNotetaker implements WorkspacePurgeContributor {

    private final JdbcTemplate jdbc;
    private final RecallBotErasureSweeper sweeper;
    private final Clock clock;

    @Override
    public void beforePurge(UUID workspaceId) {
        int queued = jdbc.update("""
                INSERT INTO app_lm_recall_bot_erasure (recall_bot_id, call_back, delete_media)
                SELECT recall_bot_id, status IN ('SCHEDULED', 'JOINING', 'RECORDING', 'PROCESSING'), NOT media_deleted
                FROM app_lm_meeting_recording
                WHERE workspace_id = ? AND recall_bot_id IS NOT NULL
                  AND (status IN ('SCHEDULED', 'JOINING', 'RECORDING', 'PROCESSING') OR NOT media_deleted)
                ON CONFLICT (recall_bot_id) DO NOTHING
                """, workspaceId);
        if (queued > 0) {
            sweeper.sweepAt(clock.instant());
        }
    }
}
