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
 * One background thread per instance holding one {@code LISTEN}ing connection, forwarding every
 * notification to the handler that owns its channel. The connection is borrowed from the pool and
 * held for the life of the app — {@code LISTEN} is session state, so it cannot share a pooled
 * connection with ordinary traffic — and any failure is answered by borrowing a fresh one after a
 * pause, because a dropped listener degrades a stream to its fallback poll rather than breaking
 * anything.
 *
 * <p><b>One connection, many channels.</b> A Postgres session may subscribe to any number of
 * channels and a delivered notification names the one it came from, so every feature that needs a
 * stream contributes a {@link PostgresNotificationHandler} rather than a second listener. With
 * {@code DB_POOL_MAX} at 5, a per-feature listener thread would spend a fifth of the instance's
 * connection budget on a subscription.
 */
@Component
@Slf4j
public class PostgresStreamListener implements SmartLifecycle {

    private static final int WAIT_FOR_NOTIFICATIONS_MS = 1000;
    private static final long RECONNECT_PAUSE_MS = 3000;

    /**
     * {@code LISTEN} takes no bind parameter, so a channel name is concatenated into SQL. While the
     * names were private constants that was safe by inspection; an injectable SPI makes it a surface,
     * and this closes it at startup instead of trusting every future implementor.
     */
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
                // Two handlers on one channel would each see the other's payloads and log them as
                // malformed, which reads as a serialisation bug rather than a wiring one.
                throw new IllegalStateException("Channel " + channel + " is claimed by both "
                        + clash.getClass().getName() + " and " + handler.getClass().getName());
            }
        }
        return Map.copyOf(byChannel);
    }

    @Override
    public void start() {
        // SmartLifecycle will not start a running bean, but a context restarted in a test can — and a
        // second call here would leak a thread holding a second LISTEN connection out of a pool of five.
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
                    // LISTEN sticks to the physical connection, and close() only returns it to the
                    // pool — without this the next borrower inherits every subscription.
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

    /**
     * Per-notification try/catch, because this is one thread serving every stream on the instance: a
     * handler that throws must cost its own event, not everybody's subscription.
     */
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
