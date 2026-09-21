package app.lightmove.api.assistant.stream;

import app.lightmove.api.core.stream.PostgresNotificationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Wakes this instance's subscribers when a turn appends somewhere.
 *
 * <p>Its own channel rather than an extra kind on the project stream's, deliberately: that stream's
 * dispatch drops a kind it does not recognise, so during a rolling deploy an older instance would
 * silently discard assistant events instead of failing visibly. A channel an old instance never
 * subscribed to simply delivers nothing to it, which is a stream that reconnects rather than a
 * conversation with holes in it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantTurnNotificationHandler implements PostgresNotificationHandler {

    private final AssistantTurnStreamRegistry registry;
    private final ObjectMapper json;

    @Override
    public String channel() {
        return AssistantTurnNotification.CHANNEL;
    }

    @Override
    public void handle(String payload) {
        try {
            AssistantTurnNotification notification =
                    json.readValue(payload, AssistantTurnNotification.class);
            registry.wake(notification.turnId(), notification.seq());
        } catch (Exception malformed) {
            log.warn("Ignoring malformed assistant turn payload: {}", payload);
        }
    }
}
