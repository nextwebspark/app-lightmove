package app.lightmove.api.platform.service;

import app.lightmove.api.platform.model.StoredPlatformSetting;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The variables screen's reads and writes of {@code app_lm_platform_setting}. */
@Component
@RequiredArgsConstructor
class PlatformSettingStore {

    private static final String SELECT = """
            SELECT s.setting_key, s.value, u.full_name, s.updated_at
            FROM app_lm_platform_setting s
            LEFT JOIN app_lm_user u ON u.id = s.updated_by
            """;

    private final JdbcTemplate jdbc;

    List<StoredPlatformSetting> all() {
        return jdbc.query(SELECT, PlatformSettingStore::row);
    }

    Optional<StoredPlatformSetting> find(String key) {
        return jdbc.query(SELECT + " WHERE s.setting_key = ?", PlatformSettingStore::row, key).stream().findFirst();
    }

    void write(String key, String value, UUID userId) {
        jdbc.update("""
                INSERT INTO app_lm_platform_setting (setting_key, value, updated_by, updated_at)
                VALUES (?, ?, ?, now())
                ON CONFLICT (setting_key) DO UPDATE
                    SET value = excluded.value, updated_by = excluded.updated_by, updated_at = excluded.updated_at
                """, key, value, userId);
    }

    void delete(String key) {
        jdbc.update("DELETE FROM app_lm_platform_setting WHERE setting_key = ?", key);
    }

    private static StoredPlatformSetting row(ResultSet row, int index) throws SQLException {
        Timestamp updatedAt = row.getTimestamp("updated_at");
        return new StoredPlatformSetting(row.getString("setting_key"), row.getString("value"),
                row.getString("full_name"), updatedAt == null ? null : updatedAt.toInstant());
    }
}
