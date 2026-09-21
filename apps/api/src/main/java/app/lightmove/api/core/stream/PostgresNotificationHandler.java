package app.lightmove.api.core.stream;

/**
 * One feature's side of the instance's single {@code LISTEN}ing connection.
 *
 * <p>A Postgres session can subscribe to many channels, and a delivered notification names the one it
 * came from — so a second stream costs a channel rather than a connection. That distinction is the
 * whole reason this interface exists: {@code DB_POOL_MAX} is 5, and a second listener thread holding
 * a second connection is a fifth of the instance's budget spent on a subscription.
 *
 * <p>Implementations are found by {@link PostgresStreamListener}, which owns the thread and the
 * connection. A handler is called on that one thread, so it must not block: anything slow belongs on
 * the implementor's own executor, or one slow consumer stalls every stream on the instance.
 */
public interface PostgresNotificationHandler {

    /**
     * The channel to subscribe to. Validated as a Postgres identifier at startup, because
     * {@code LISTEN} takes no bind parameter and the name is concatenated into SQL.
     */
    String channel();

    /** The raw {@code pg_notify} payload. Malformed input is this handler's to log and drop. */
    void handle(String payload);
}
