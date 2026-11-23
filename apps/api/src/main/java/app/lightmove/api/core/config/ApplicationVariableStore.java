package app.lightmove.api.core.config;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads the stored overrides of application variables. A platform table: no workspace. Writes are the console's. */
@Repository
@RequiredArgsConstructor
public class ApplicationVariableStore {

    private final JdbcClient jdbc;

    /** As stored: a key off the allowlist or a value out of bounds is the reader's to drop. */
    public Map<String, String> overrides() {
        Map<String, String> stored = new HashMap<>();
        jdbc.sql("SELECT key, value FROM app_lm_platform_setting")
                .query(row -> {
                    stored.put(row.getString("key"), row.getString("value"));
                });
        return stored;
    }
}
