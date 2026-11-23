package app.lightmove.api.core.config;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The stored overrides of application variables and the record of every change. Platform tables: no workspace. */
@Repository
@RequiredArgsConstructor
public class ApplicationVariableStore {

    private static final String CHANGE_COLUMNS = """
            c.key, c.old_value, c.new_value, c.note, c.changed_at, author.full_name AS changed_by_name
            """;

    private final JdbcClient jdbc;

    /** Every stored override, as stored: a key off the allowlist or a value out of bounds is the reader's to drop. */
    public Map<String, String> overrides() {
        Map<String, String> stored = new HashMap<>();
        jdbc.sql("SELECT key, value FROM app_lm_platform_setting")
                .query(row -> {
                    stored.put(row.getString("key"), row.getString("value"));
                });
        return stored;
    }

    public Optional<String> overrideOf(String key) {
        return jdbc.sql("SELECT value FROM app_lm_platform_setting WHERE key = :key")
                .param("key", key)
                .query(String.class)
                .optional();
    }

    /** Held to the end of the transaction, so two changes of one variable record their old values in order. */
    public void lock(String key) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", "setting:" + key)
                .query(row -> { });
    }

    public void override(String key, String value, UUID userId) {
        jdbc.sql("""
                        INSERT INTO app_lm_platform_setting (key, value, updated_by) VALUES (:key, :value, :userId)
                        ON CONFLICT (key) DO UPDATE
                            SET value = EXCLUDED.value, updated_by = EXCLUDED.updated_by, updated_at = now()
                        """)
                .param("key", key)
                .param("value", value)
                .param("userId", userId)
                .update();
    }

    public void removeOverride(String key) {
        jdbc.sql("DELETE FROM app_lm_platform_setting WHERE key = :key").param("key", key).update();
    }

    public void recordChange(String key, String oldValue, String newValue, String note, UUID userId) {
        jdbc.sql("""
                        INSERT INTO app_lm_platform_setting_change (key, old_value, new_value, note, changed_by)
                        VALUES (:key, :oldValue, :newValue, :note, :userId)
                        """)
                .param("key", key)
                .param("oldValue", oldValue)
                .param("newValue", newValue)
                .param("note", note)
                .param("userId", userId)
                .update();
    }

    /** Newest first. */
    public List<ApplicationVariableChange> changesOf(String key) {
        return jdbc.sql("SELECT " + CHANGE_COLUMNS + """
                        FROM app_lm_platform_setting_change c
                        LEFT JOIN app_lm_user author ON author.id = c.changed_by
                        WHERE c.key = :key
                        ORDER BY c.id DESC
                        """)
                .param("key", key)
                .query(ApplicationVariableStore::changeOf)
                .list();
    }

    /** The latest change of each variable that has one, by key. */
    public Map<String, ApplicationVariableChange> latestChanges() {
        Map<String, ApplicationVariableChange> latest = new HashMap<>();
        jdbc.sql("SELECT DISTINCT ON (c.key) " + CHANGE_COLUMNS + """
                        FROM app_lm_platform_setting_change c
                        LEFT JOIN app_lm_user author ON author.id = c.changed_by
                        ORDER BY c.key, c.id DESC
                        """)
                .query(ApplicationVariableStore::changeOf)
                .list()
                .forEach(change -> latest.put(change.key(), change));
        return latest;
    }

    private static ApplicationVariableChange changeOf(ResultSet row, int index) throws SQLException {
        return new ApplicationVariableChange(row.getString("key"), row.getString("old_value"),
                row.getString("new_value"), row.getString("note"), row.getString("changed_by_name"),
                row.getTimestamp("changed_at").toInstant());
    }
}
