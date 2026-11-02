package app.lightmove.api.core.platformconfig.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.platformconfig.constant.PlatformPrompt;
import app.lightmove.api.core.platformconfig.constant.PlatformSetting;
import app.lightmove.api.core.platformconfig.model.PlatformConfigSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The overrides this instance runs on, held as one immutable snapshot. A read reloads it when a NOTIFY marked it
 * stale, or once {@code config-check-interval} has passed and the stored version moved; one thread checks while the
 * rest keep the snapshot they have. A failed check keeps the last snapshot, so a database blip never turns a super
 * admin's override back into the default mid-run.
 */
@Component
@Slf4j
public class PlatformOverrides {

    private final PlatformConfigStore store;
    private final Clock clock;
    private final Duration checkInterval;
    private final ReentrantLock checking = new ReentrantLock();

    private volatile PlatformConfigSnapshot snapshot = PlatformConfigSnapshot.unloaded();
    private volatile Instant checkedAt = Instant.MIN;
    private volatile boolean stale = true;

    @Autowired
    public PlatformOverrides(PlatformConfigStore store, Clock clock, LightMoveProperties properties) {
        this(store, clock, properties.platform().configCheckInterval());
    }

    PlatformOverrides(PlatformConfigStore store, Clock clock, Duration checkInterval) {
        this.store = store;
        this.clock = clock;
        this.checkInterval = checkInterval;
    }

    public PlatformConfigSnapshot current() {
        if (stale || checkInterval.isZero() || clock.instant().isAfter(checkedAt.plus(checkInterval))) {
            check();
        }
        return snapshot;
    }

    /** A NOTIFY, or this instance's own commit: the next read reloads whatever the version says. */
    public void markStale() {
        stale = true;
    }

    private void check() {
        if (!snapshot.isLoaded()) {
            checking.lock();
        } else if (!checking.tryLock()) {
            return;
        }
        try {
            boolean forced = stale || !snapshot.isLoaded();
            stale = false;
            long stored = store.currentVersion();
            if (forced || stored != snapshot.version()) {
                snapshot = load(stored);
            }
            checkedAt = clock.instant();
        } catch (RuntimeException unreachable) {
            stale = true;
            log.warn("Could not read the platform overrides; keeping version {}", snapshot.version(), unreachable);
        } finally {
            checking.unlock();
        }
    }

    /**
     * The version is read before the rows, so a write landing between the two leaves rows newer than the version
     * recorded, and the next check reloads; never the reverse, which would keep stale rows under a current version.
     */
    private PlatformConfigSnapshot load(long version) {
        Map<PlatformSetting, Object> settings = new EnumMap<>(PlatformSetting.class);
        store.settingValues().forEach((key, value) -> parsed(key, value).ifPresent(typed -> settings.put(
                PlatformSetting.byKey(key).orElseThrow(), typed)));
        Map<PlatformPrompt, String> prompts = new EnumMap<>(PlatformPrompt.class);
        store.activePromptBodies().forEach((promptId, body) -> PlatformPrompt.byId(promptId).ifPresentOrElse(
                prompt -> prompts.put(prompt, body),
                () -> log.warn("Ignoring an override of prompt {}, which this build does not list", promptId)));
        log.info("Loaded platform overrides version {}: {} values, {} prompts", version, settings.size(), prompts.size());
        return new PlatformConfigSnapshot(version, settings, prompts, clock.instant());
    }

    /** A key a later build dropped, or a value its bounds refuse, is skipped and runs on the default. */
    private static Optional<Object> parsed(String key, String value) {
        Optional<PlatformSetting> setting = PlatformSetting.byKey(key);
        if (setting.isEmpty()) {
            log.warn("Ignoring an override of {}, which this build does not allow", key);
            return Optional.empty();
        }
        try {
            return Optional.of(setting.get().parse(value));
        } catch (ApiException outOfBounds) {
            log.warn("Ignoring the stored override of {}, which this build's bounds refuse", key);
            return Optional.empty();
        }
    }
}
