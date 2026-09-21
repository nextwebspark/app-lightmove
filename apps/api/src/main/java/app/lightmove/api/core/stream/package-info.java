/**
 * <b>Project stream — the server telling an open screen that a mandate's data changed.</b> A browser
 * holds one SSE stream per project and refetches through the ordinary guarded endpoints when an event
 * arrives; the events carry a kind and nothing else, so nothing readable travels outside an
 * authorised read.
 *
 * <p>The plumbing under it is shared rather than project-specific: {@link
 * app.lightmove.api.core.stream.PostgresStreamListener} holds the instance's single {@code LISTEN}ing
 * connection and dispatches by channel to a {@link
 * app.lightmove.api.core.stream.PostgresNotificationHandler}, so a second stream costs a channel
 * rather than a fifth of the connection pool. The project stream is one such handler.
 *
 * <p>The write side goes through Postgres rather than calling the emitters directly, for two reasons
 * that are both load-bearing. Cloud Run runs up to two instances, and the instance that commits an
 * enrichment is not necessarily the one holding the browser's stream — {@code NOTIFY} is the only
 * channel both already share. And Postgres delivers a {@code pg_notify} issued inside a transaction
 * <i>at commit</i>, so an event can never reach the browser before the data it announces is visible,
 * and a rolled-back write announces nothing.
 */
package app.lightmove.api.core.stream;
