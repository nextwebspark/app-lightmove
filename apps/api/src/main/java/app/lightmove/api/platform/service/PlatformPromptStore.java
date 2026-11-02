package app.lightmove.api.platform.service;

import app.lightmove.api.platform.model.StoredPromptVersion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The prompts screen's reads and writes of {@code app_lm_platform_prompt}, whose rows are never edited but for active. */
@Component
@RequiredArgsConstructor
class PlatformPromptStore {

    private static final String SELECT_VERSIONS = """
            SELECT p.prompt_id, p.version, p.active, u.full_name, p.created_at
            FROM app_lm_platform_prompt p
            LEFT JOIN app_lm_user u ON u.id = p.created_by
            """;

    private final JdbcTemplate jdbc;

    List<StoredPromptVersion> activeVersions() {
        return jdbc.query(SELECT_VERSIONS + " WHERE p.active", PlatformPromptStore::row);
    }

    List<StoredPromptVersion> versionsOf(String promptId) {
        return jdbc.query(SELECT_VERSIONS + " WHERE p.prompt_id = ? ORDER BY p.version DESC",
                PlatformPromptStore::row, promptId);
    }

    Optional<Integer> activeVersionOf(String promptId) {
        return jdbc.queryForList("SELECT version FROM app_lm_platform_prompt WHERE prompt_id = ? AND active",
                Integer.class, promptId).stream().findFirst();
    }

    Optional<String> activeBodyOf(String promptId) {
        return jdbc.queryForList("SELECT body FROM app_lm_platform_prompt WHERE prompt_id = ? AND active",
                String.class, promptId).stream().findFirst();
    }

    Optional<String> bodyOf(String promptId, int version) {
        return jdbc.queryForList("SELECT body FROM app_lm_platform_prompt WHERE prompt_id = ? AND version = ?",
                String.class, promptId, version).stream().findFirst();
    }

    /** Makes {@code body} the prompt's next version and the one in use. */
    int saveActive(String promptId, String body, UUID userId) {
        deactivate(promptId);
        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), 0) + 1 FROM app_lm_platform_prompt WHERE prompt_id = ?",
                Integer.class, promptId);
        jdbc.update("""
                INSERT INTO app_lm_platform_prompt (prompt_id, version, body, active, created_by)
                VALUES (?, ?, ?, true, ?)
                """, promptId, next, body, userId);
        return next;
    }

    /** The shipped file answers again; every saved version stays. */
    void deactivate(String promptId) {
        jdbc.update("UPDATE app_lm_platform_prompt SET active = false WHERE prompt_id = ? AND active", promptId);
    }

    private static StoredPromptVersion row(ResultSet row, int index) throws SQLException {
        return new StoredPromptVersion(row.getString("prompt_id"), row.getInt("version"), row.getBoolean("active"),
                row.getString("full_name"), row.getTimestamp("created_at").toInstant());
    }
}
