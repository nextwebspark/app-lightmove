package app.lightmove.api.core.security.rbac;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** When each platform admin last entered a fresh two-factor code (V233): one row per person, overwritten. */
@Repository
@RequiredArgsConstructor
public class PlatformStepUpStore {

    private final JdbcClient jdbc;

    public void record(UUID userId, Instant verifiedAt) {
        jdbc.sql("""
                        INSERT INTO app_lm_platform_step_up (user_id, verified_at) VALUES (:userId, :verifiedAt)
                        ON CONFLICT (user_id) DO UPDATE SET verified_at = EXCLUDED.verified_at
                        """)
                .param("userId", userId)
                .param("verifiedAt", Timestamp.from(verifiedAt))
                .update();
    }

    public Optional<Instant> verifiedAt(UUID userId) {
        return jdbc.sql("SELECT verified_at FROM app_lm_platform_step_up WHERE user_id = :userId")
                .param("userId", userId)
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant);
    }
}
