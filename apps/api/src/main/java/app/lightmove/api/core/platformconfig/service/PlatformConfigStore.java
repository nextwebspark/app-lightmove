package app.lightmove.api.core.platformconfig.service;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The stored overrides as every instance reads them. Plain JDBC: the snapshot is read whole and never edited as an
 * aggregate, and the version row is one counter.
 */
@Component
@RequiredArgsConstructor
public class PlatformConfigStore {

    private final JdbcTemplate jdbc;

    public long currentVersion() {
        Long version = jdbc.queryForObject("SELECT version FROM app_lm_platform_config_state WHERE id = 1", Long.class);
        return version == null ? 0 : version;
    }

    /**
     * Raises the version in the caller's transaction. The update locks the one row until commit, so platform writes
     * are serialised behind it and a prompt's next version number cannot be taken twice.
     */
    public long raiseVersion() {
        Long version = jdbc.queryForObject(
                "UPDATE app_lm_platform_config_state SET version = version + 1, updated_at = now() WHERE id = 1 "
                        + "RETURNING version", Long.class);
        if (version == null) {
            throw new IllegalStateException("app_lm_platform_config_state has no row; V181 seeds it");
        }
        return version;
    }

    public Map<String, String> settingValues() {
        Map<String, String> values = new HashMap<>();
        jdbc.query("SELECT setting_key, value FROM app_lm_platform_setting",
                row -> { values.put(row.getString("setting_key"), row.getString("value")); });
        return values;
    }

    public Map<String, String> activePromptBodies() {
        Map<String, String> bodies = new HashMap<>();
        jdbc.query("SELECT prompt_id, body FROM app_lm_platform_prompt WHERE active",
                row -> { bodies.put(row.getString("prompt_id"), row.getString("body")); });
        return bodies;
    }
}
