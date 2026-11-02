package app.lightmove.api.core.platformconfig.service;

import app.lightmove.api.core.stream.PostgresNotificationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Another instance saved, reset or refreshed an override: this one reloads on its next read. Only marks, never
 * loads, because a handler runs on the listener's one thread and must not block it on a query.
 */
@Component
@RequiredArgsConstructor
public class PlatformConfigNotificationHandler implements PostgresNotificationHandler {

    static final String CHANNEL = "lm_platform_config";

    private final PlatformOverrides overrides;

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public void handle(String payload) {
        overrides.markStale();
    }
}
