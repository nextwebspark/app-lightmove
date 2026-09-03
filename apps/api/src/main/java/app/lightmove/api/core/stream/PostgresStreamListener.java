package app.lightmove.api.core.stream;

import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * One background thread per instance holding one {@code LISTEN}ing connection, forwarding every
 * notification to this instance's own emitters. The connection is borrowed from the pool and held for
 * the life of the app — {@code LISTEN} is session state, so it cannot share a pooled connection with
 * ordinary traffic — and any failure is answered by borrowing a fresh one after a pause, because a
 * dropped listener degrades the grid to its fallback poll rather than breaking anything.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PostgresStreamListener implements SmartLifecycle {

    static final String CHANNEL = "lm_project_stream";
    private static final int WAIT_FOR_NOTIFICATIONS_MS = 1000;
    private static final long RECONNECT_PAUSE_MS = 3000;

    private final DataSource dataSource;
    private final ProjectStreamRegistry registry;
    private final ObjectMapper json;

    private volatile boolean running;
    private Thread listener;

    @Override
    public void start() {
        running = true;
        listener = new Thread(this::listen, "project-stream-listener");
        listener.setDaemon(true);
        listener.start();
    }

    @Override
    public void stop() {
        running = false;
        if (listener != null) {
            listener.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void listen() {
        while (running) {
            try (Connection connection = dataSource.getConnection()) {
                try {
                    try (Statement subscribe = connection.createStatement()) {
                        subscribe.execute("LISTEN " + CHANNEL);
                    }
                    PGConnection postgres = connection.unwrap(PGConnection.class);
                    while (running) {
                        PGNotification[] delivered = postgres.getNotifications(WAIT_FOR_NOTIFICATIONS_MS);
                        if (delivered == null) {
                            continue;
                        }
                        for (PGNotification notification : delivered) {
                            forward(notification.getParameter());
                        }
                    }
                } finally {
                    // LISTEN sticks to the physical connection, and close() only returns it to the
                    // pool — without this the next borrower inherits the subscription.
                    unlistenQuietly(connection);
                }
            } catch (Exception connectionLost) {
                if (running) {
                    log.warn("Project stream listener lost its connection; reconnecting", connectionLost);
                    pause();
                }
            }
        }
    }

    private void forward(String payload) {
        try {
            ProjectStreamNotification notification =
                    json.readValue(payload, ProjectStreamNotification.class);
            registry.broadcast(notification.projectId(), notification.kind());
        } catch (Exception malformed) {
            log.warn("Ignoring malformed project stream payload: {}", payload);
        }
    }

    private void unlistenQuietly(Connection connection) {
        try (Statement unsubscribe = connection.createStatement()) {
            unsubscribe.execute("UNLISTEN *");
        } catch (Exception alreadyBroken) {
            // A dead connection cannot unlisten and will not rejoin the pool either.
        }
    }

    private void pause() {
        try {
            Thread.sleep(RECONNECT_PAUSE_MS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
