package app.lightmove.api.core.stream;

import java.sql.Connection;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * One thread per instance holding one pooled {@code LISTEN} connection for the app's life, forwarding
 * each notification to its channel's handler and reconnecting after a pause on failure. One connection
 * serves every channel: with {@code DB_POOL_MAX} at 5, a listener per feature would cost a fifth of the pool.
 */
@Component
@Slf4j
public class PostgresStreamListener implements SmartLifecycle {

    private static final int WAIT_FOR_NOTIFICATIONS_MS = 1000;
    private static final long RECONNECT_PAUSE_MS = 3000;

    /** {@code LISTEN} takes no bind parameter, so a channel name is concatenated into SQL: checked at startup. */
    private static final Pattern LEGAL_CHANNEL = Pattern.compile("^[a-z_][a-z0-9_]{0,62}$");

    private final DataSource dataSource;
    private final Map<String, PostgresNotificationHandler> handlers;

    private volatile boolean running;
    private Thread listener;

    public PostgresStreamListener(DataSource dataSource, List<PostgresNotificationHandler> handlers) {
        this.dataSource = dataSource;
        this.handlers = index(handlers);
    }

    private static Map<String, PostgresNotificationHandler> index(
            List<PostgresNotificationHandler> handlers) {
        Map<String, PostgresNotificationHandler> byChannel = new LinkedHashMap<>();
        for (PostgresNotificationHandler handler : handlers) {
            String channel = handler.channel();
            if (channel == null || !LEGAL_CHANNEL.matcher(channel).matches()) {
                throw new IllegalStateException(handler.getClass().getName()
                        + " names an illegal Postgres channel: " + channel);
            }
            PostgresNotificationHandler clash = byChannel.put(channel, handler);
            if (clash != null) {
                throw new IllegalStateException("Channel " + channel + " is claimed by both "
                        + clash.getClass().getName() + " and " + handler.getClass().getName());
            }
        }
        return Map.copyOf(byChannel);
    }

    @Override
    public void start() {
        // A context restarted in a test can call this twice, leaking a second LISTEN connection.
        if (running) {
            return;
        }
        if (handlers.isEmpty()) {
            log.warn("No notification handlers registered; not holding a LISTEN connection");
            return;
        }
        running = true;
        listener = new Thread(this::listen, "postgres-stream-listener");
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
                    subscribe(connection);
                    PGConnection postgres = connection.unwrap(PGConnection.class);
                    while (running) {
                        PGNotification[] delivered = postgres.getNotifications(WAIT_FOR_NOTIFICATIONS_MS);
                        if (delivered == null) {
                            continue;
                        }
                        for (PGNotification notification : delivered) {
                            forward(notification.getName(), notification.getParameter());
                        }
                    }
                } finally {
                    // LISTEN sticks to the physical connection; the next borrower would inherit it.
                    unlistenQuietly(connection);
                }
            } catch (Exception connectionLost) {
                if (running) {
                    log.warn("Postgres stream listener lost its connection; reconnecting", connectionLost);
                    pause();
                }
            }
        }
    }

    private void subscribe(Connection connection) throws Exception {
        for (String channel : handlers.keySet()) {
            try (Statement subscribe = connection.createStatement()) {
                subscribe.execute("LISTEN " + channel);
            }
        }
    }

    /** Per-notification catch: one thread serves every stream, so a throwing handler costs only its event. */
    private void forward(String channel, String payload) {
        PostgresNotificationHandler handler = handlers.get(channel);
        if (handler == null) {
            log.warn("Ignoring a notification on unhandled channel {}", channel);
            return;
        }
        try {
            handler.handle(payload);
        } catch (Exception failed) {
            log.warn("Handler for channel {} failed on a notification", channel, failed);
        }
    }

    private void unlistenQuietly(Connection connection) {
        try (Statement unsubscribe = connection.createStatement()) {
            unsubscribe.execute("UNLISTEN *");
        } catch (Exception alreadyBroken) {
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
