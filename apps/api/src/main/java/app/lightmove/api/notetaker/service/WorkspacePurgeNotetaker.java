package app.lightmove.api.notetaker.service;

import app.lightmove.api.workspace.service.WorkspacePurgeContributor;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Before a deleted workspace's recordings go, Recall's copies go: any bot somehow still live is called back, and the
 * media of every call still held there is deleted. The rows are the only record of which bots were the workspace's.
 */
@Component
@RequiredArgsConstructor
class WorkspacePurgeNotetaker implements WorkspacePurgeContributor {

    private final JdbcTemplate jdbc;
    private final NotetakerService notetaker;

    @Override
    public void beforePurge(UUID workspaceId) {
        List<RecallBot> bots = jdbc.query("""
                SELECT recall_bot_id, status IN ('SCHEDULED', 'JOINING', 'RECORDING', 'PROCESSING') AS live,
                       NOT media_deleted AS media_held
                FROM app_lm_meeting_recording
                WHERE workspace_id = ? AND recall_bot_id IS NOT NULL
                """, (row, index) -> new RecallBot(row.getString("recall_bot_id"), row.getBoolean("live"),
                        row.getBoolean("media_held")), workspaceId);
        bots.forEach(bot -> {
            if (bot.live()) {
                notetaker.removeQuietly(bot.botId());
            }
            if (bot.mediaHeld()) {
                notetaker.deleteMediaQuietly(bot.botId());
            }
        });
    }

    private record RecallBot(String botId, boolean live, boolean mediaHeld) {}
}
