/**
 * <b>Project stream — the server telling an open screen that a mandate's data changed.</b> Events carry a kind only;
 * the browser refetches through guarded endpoints. One shared {@code LISTEN} connection dispatches by channel. Writes go
 * through Postgres {@code NOTIFY}: it reaches every instance and is delivered at commit, so nothing is announced early.
 */
package app.lightmove.api.core.stream;
