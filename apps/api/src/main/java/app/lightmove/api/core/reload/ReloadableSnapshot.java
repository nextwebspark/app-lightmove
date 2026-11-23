package app.lightmove.api.core.reload;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * What a {@link PlatformConfigListener} holds in memory: read once, served until {@link #drop}. Never throws: a read
 * that fails serves the fallback and is tried again after a pause. One caller reads at a time and the rest wait for
 * its answer.
 */
@Slf4j
public final class ReloadableSnapshot<T> {

    private final String name;
    private final String fallbackName;
    private final Supplier<T> read;
    private final T fallback;
    private final Duration retryAfterFailedRead;
    private final Clock clock;

    // Not a monitor: a virtual thread blocked on the read inside one would pin its carrier.
    private final ReentrantLock readLock = new ReentrantLock();
    private final Object cacheLock = new Object();
    private volatile T held;
    private volatile long retryReadAtMillis;
    private long drops;

    /** {@code name} and {@code fallbackName} word the failed read's log line. */
    public ReloadableSnapshot(String name, String fallbackName, Supplier<T> read, T fallback,
                              Duration retryAfterFailedRead, Clock clock) {
        this.name = name;
        this.fallbackName = fallbackName;
        this.read = read;
        this.fallback = fallback;
        this.retryAfterFailedRead = retryAfterFailedRead;
        this.clock = clock;
    }

    public T get() {
        T current = held;
        if (current != null) {
            return current;
        }
        if (clock.millis() < retryReadAtMillis) {
            return fallback;
        }
        readLock.lock();
        try {
            return held != null ? held : readOnce();
        } finally {
            readLock.unlock();
        }
    }

    public void drop() {
        synchronized (cacheLock) {
            drops++;
            held = null;
            retryReadAtMillis = 0;
        }
    }

    private T readOnce() {
        if (clock.millis() < retryReadAtMillis) {
            return fallback;
        }
        long dropsBeforeTheRead;
        synchronized (cacheLock) {
            dropsBeforeTheRead = drops;
        }
        T current;
        try {
            current = read.get();
        } catch (RuntimeException unreadable) {
            retryReadAtMillis = clock.millis() + retryAfterFailedRead.toMillis();
            log.error("The {} could not be read; serving the {} and retrying in {}s", name, fallbackName,
                    retryAfterFailedRead.toSeconds(), unreadable);
            return fallback;
        }
        // A drop during the read means this value may predate the change: kept, it would stay until the next bump,
        // since the backstop check has already seen the new number. Answer this call with it and read again next.
        synchronized (cacheLock) {
            if (drops == dropsBeforeTheRead) {
                held = current;
            }
        }
        return current;
    }
}
