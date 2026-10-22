package app.lightmove.api.core.stream;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Announces that a mandate's data changed; a typed front on {@link PostgresNotificationPublisher}.
 * {@code MANDATORY} is repeated here so a publish outside a transaction is refused at this bean.
 */
@Component
@RequiredArgsConstructor
public class ProjectStreamPublisher {

    private final PostgresNotificationPublisher notifications;

    /**
     * Once per transaction for each mandate and kind: Postgres would fold the repeats at delivery anyway, but each is
     * a round trip, and an entity listener announces every row an archive stops or a dispatch batch writes.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(UUID projectId, ProjectStreamKind kind) {
        ProjectStreamNotification notification = new ProjectStreamNotification(projectId, kind.wire());
        if (publishedInThisTransaction().add(notification)) {
            notifications.publish(ProjectStreamNotificationHandler.CHANNEL, notification);
        }
    }

    // Held by a synchronization rather than a bound resource: the manager clears synchronizations with the
    // transaction, so nothing outlives it on the thread, and a REQUIRES_NEW inside gets a set of its own.
    private static Set<ProjectStreamNotification> publishedInThisTransaction() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return new HashSet<>();
        }
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            if (synchronization instanceof PublishedNotifications published) {
                return published.notifications;
            }
        }
        PublishedNotifications published = new PublishedNotifications();
        TransactionSynchronizationManager.registerSynchronization(published);
        return published.notifications;
    }

    private static final class PublishedNotifications implements TransactionSynchronization {
        private final Set<ProjectStreamNotification> notifications = new HashSet<>();
    }
}
