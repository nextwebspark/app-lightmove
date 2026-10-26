package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.stream.PostgresNotificationPublisher;
import app.lightmove.api.notetaker.model.CopilotStreamNotification;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Announces a live call's copilot changed, at the commit that changed it, once per recording per transaction. */
@Component
@RequiredArgsConstructor
public class CopilotStreamPublisher {

    private final PostgresNotificationPublisher notifications;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(UUID recordingId) {
        if (publishedInThisTransaction().add(recordingId)) {
            notifications.publish(CopilotStreamNotificationHandler.CHANNEL, new CopilotStreamNotification(recordingId));
        }
    }

    private static Set<UUID> publishedInThisTransaction() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return new HashSet<>();
        }
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            if (synchronization instanceof PublishedRecordings published) {
                return published.recordings;
            }
        }
        PublishedRecordings published = new PublishedRecordings();
        TransactionSynchronizationManager.registerSynchronization(published);
        return published.recordings;
    }

    private static final class PublishedRecordings implements TransactionSynchronization {
        private final Set<UUID> recordings = new HashSet<>();
    }
}
