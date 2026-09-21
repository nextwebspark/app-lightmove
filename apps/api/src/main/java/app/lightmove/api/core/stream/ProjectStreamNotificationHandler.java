package app.lightmove.api.core.stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Turns a project-stream notification back into a broadcast to this instance's own emitters. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectStreamNotificationHandler implements PostgresNotificationHandler {

    static final String CHANNEL = "lm_project_stream";

    private final ProjectStreamRegistry registry;
    private final ObjectMapper json;

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public void handle(String payload) {
        try {
            ProjectStreamNotification notification =
                    json.readValue(payload, ProjectStreamNotification.class);
            ProjectStreamKind.fromWire(notification.kind()).ifPresentOrElse(
                    kind -> registry.broadcast(notification.projectId(), kind),
                    () -> log.warn("Ignoring project stream payload naming an unknown kind: {}", payload));
        } catch (Exception malformed) {
            log.warn("Ignoring malformed project stream payload: {}", payload);
        }
    }
}
