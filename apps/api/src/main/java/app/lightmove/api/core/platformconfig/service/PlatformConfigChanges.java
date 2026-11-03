package app.lightmove.api.core.platformconfig.service;

import app.lightmove.api.core.platformconfig.model.PlatformConfigChanged;
import app.lightmove.api.core.stream.PostgresNotificationPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Every platform write, and Refresh, goes through {@link #announce}: the version is raised and the NOTIFY issued in
 * the writer's transaction, so every instance hears of the change at its commit and never of one rolled back.
 */
@Component
@RequiredArgsConstructor
public class PlatformConfigChanges {

    private final PlatformConfigStore store;
    private final PostgresNotificationPublisher notifications;
    private final PlatformOverrides overrides;

    /** The version this change becomes. Call it first in the write: its row lock serialises platform writes. */
    @Transactional(propagation = Propagation.MANDATORY)
    public long announce() {
        long version = store.raiseVersion();
        notifications.publish(PlatformConfigNotificationHandler.CHANNEL, new PlatformConfigChanged(version));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                overrides.markStale();
            }
        });
        return version;
    }
}
