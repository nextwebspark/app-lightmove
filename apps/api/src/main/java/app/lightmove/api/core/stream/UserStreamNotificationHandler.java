package app.lightmove.api.core.stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Turns a person-stream notification back into a nudge to this instance's own emitters for that person. */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserStreamNotificationHandler implements PostgresNotificationHandler {

    static final String CHANNEL = "lm_user_stream";

    private final UserStreamRegistry registry;
    private final ObjectMapper json;

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public void handle(String payload) {
        try {
            UserStreamNotification notification = json.readValue(payload, UserStreamNotification.class);
            registry.nudge(notification.userId(), notification.workspaceId());
        } catch (Exception malformed) {
            log.warn("Ignoring malformed user stream payload: {}", payload);
        }
    }
}
