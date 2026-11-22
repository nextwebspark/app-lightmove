package app.lightmove.api.core.reload;

/**
 * Something an instance holds in memory that a platform change makes stale. {@link #reload} is called on the
 * Postgres listener's one thread, so it only drops what is cached: the next read fetches it.
 */
public interface PlatformConfigListener {

    String scope();

    void reload();
}
