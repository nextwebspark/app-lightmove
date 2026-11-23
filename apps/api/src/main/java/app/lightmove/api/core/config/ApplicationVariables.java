package app.lightmove.api.core.config;

import app.lightmove.api.core.reload.PlatformConfigListener;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * What each application variable is right now: a super admin's override, else the yml value. Overrides are held in
 * memory until a platform change drops them. Never the reason a feature fails: with the table unreadable, or a
 * stored value that is off the allowlist or out of bounds, the yml value serves.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationVariables implements PlatformConfigListener {

    public static final String SCOPE = "settings";

    private static final Duration RETRY_AFTER_FAILED_READ = Duration.ofSeconds(30);

    private final LightMoveProperties properties;
    private final ApplicationVariableStore store;
    private final Clock clock;

    private final Object cacheLock = new Object();
    private volatile Map<ApplicationVariable, Integer> overrides;
    private volatile long retryReadAtMillis;
    private long reloads;

    public int intValue(ApplicationVariable variable) {
        Integer override = overrides().get(variable);
        return override != null ? override : variable.shippedDefault(properties);
    }

    /** The overrides as stored now, past the cache: what a screen that has just written one must show. */
    public Map<ApplicationVariable, Integer> storedOverrides() {
        Map<ApplicationVariable, Integer> usable = new EnumMap<>(ApplicationVariable.class);
        store.overrides().forEach((key, value) -> {
            ApplicationVariable variable = ApplicationVariable.ofKey(key).orElse(null);
            OptionalInt number = variable == null ? OptionalInt.empty() : variable.read(value);
            if (number.isPresent()) {
                usable.put(variable, number.getAsInt());
            } else {
                log.warn("Ignoring the stored application variable {}: off the allowlist or out of bounds", key);
            }
        });
        return usable;
    }

    @Override
    public String scope() {
        return SCOPE;
    }

    @Override
    public void reload() {
        synchronized (cacheLock) {
            reloads++;
            overrides = null;
            retryReadAtMillis = 0;
        }
    }

    private Map<ApplicationVariable, Integer> overrides() {
        Map<ApplicationVariable, Integer> held = overrides;
        if (held != null) {
            return held;
        }
        if (clock.millis() < retryReadAtMillis) {
            return Map.of();
        }
        long reloadsBeforeTheRead;
        synchronized (cacheLock) {
            reloadsBeforeTheRead = reloads;
        }
        try {
            held = storedOverrides();
        } catch (RuntimeException unreadable) {
            retryReadAtMillis = clock.millis() + RETRY_AFTER_FAILED_READ.toMillis();
            log.error("The application variables could not be read; serving the configured values and retrying"
                    + " in {}s", RETRY_AFTER_FAILED_READ.toSeconds(), unreadable);
            return Map.of();
        }
        // A reload during the read means this map may predate the change: kept, it would stay until the next bump,
        // since the backstop check has already seen the new number. Answer this call with it and read again next.
        synchronized (cacheLock) {
            if (reloads == reloadsBeforeTheRead) {
                overrides = held;
            }
        }
        return held;
    }
}
