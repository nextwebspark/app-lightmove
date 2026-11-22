package app.lightmove.api.core.reload;

import app.lightmove.api.core.stream.PostgresNotificationHandler;
import app.lightmove.api.core.stream.PostgresNotificationPublisher;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes a platform change live on every instance: the writer raises its scope's number in
 * {@code app_lm_platform_config_version} and notifies, and each instance drops what it cached for that scope. A
 * notification can be missed (the listener reconnecting), so every instance also re-reads the numbers on a timer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformConfigReload implements PostgresNotificationHandler {

    static final String CHANNEL = "lm_platform_config";

    private final JdbcClient jdbc;
    private final PostgresNotificationPublisher notifications;
    private final List<PlatformConfigListener> listeners;

    private final Map<String, Long> seen = new ConcurrentHashMap<>();

    /** Inside the transaction that made the change, so no instance reloads before the change is committed. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void bump(String scope) {
        jdbc.sql("""
                        INSERT INTO app_lm_platform_config_version (scope, version) VALUES (:scope, 1)
                        ON CONFLICT (scope) DO UPDATE SET version = app_lm_platform_config_version.version + 1
                        """)
                .param("scope", scope)
                .update();
        notifications.publish(CHANNEL, scope);
    }

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public void handle(String payload) {
        reload(payload.replace("\"", ""));
    }

    @Scheduled(fixedDelayString = "${lightmove.ai.prompt-registry.reload-check}")
    public void recheck() {
        try {
            jdbc.sql("SELECT scope, version FROM app_lm_platform_config_version")
                    .query((row, index) -> Map.entry(row.getString("scope"), row.getLong("version")))
                    .list()
                    .forEach(current -> {
                        if (!Objects.equals(seen.put(current.getKey(), current.getValue()), current.getValue())) {
                            reload(current.getKey());
                        }
                    });
        } catch (RuntimeException unreadable) {
            log.warn("Could not re-read the platform configuration versions; the next check retries: {}",
                    unreadable.toString());
        }
    }

    private void reload(String scope) {
        listeners.stream().filter(listener -> listener.scope().equals(scope)).forEach(PlatformConfigListener::reload);
    }
}
