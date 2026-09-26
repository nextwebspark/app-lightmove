package app.lightmove.api.core.stream;

/**
 * One feature's channel on the instance's single {@code LISTEN} connection — a second stream costs a
 * channel, not one of {@code DB_POOL_MAX}'s 5 connections. Called on {@link PostgresStreamListener}'s
 * one thread, so it must not block.
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
