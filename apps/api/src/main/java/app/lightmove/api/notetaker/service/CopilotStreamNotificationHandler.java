package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.stream.PostgresNotificationHandler;
import app.lightmove.api.notetaker.model.CopilotStreamNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Turns a copilot notification, from whichever instance wrote the change, into a nudge to this one's windows. */
@Component
@RequiredArgsConstructor
@Slf4j
public class CopilotStreamNotificationHandler implements PostgresNotificationHandler {

    static final String CHANNEL = "lm_copilot_stream";

    private final CopilotStreamRegistry registry;
    private final ObjectMapper json;

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public void handle(String payload) {
        try {
            registry.nudge(json.readValue(payload, CopilotStreamNotification.class).recordingId());
        } catch (Exception malformed) {
            log.warn("Ignoring malformed copilot stream payload: {}", payload);
        }
    }
}
