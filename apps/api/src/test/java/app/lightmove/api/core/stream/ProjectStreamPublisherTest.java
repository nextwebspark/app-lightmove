package app.lightmove.api.core.stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** An archive stopping forty runs announces once, and the next transaction is not taken for the last one. */
class ProjectStreamPublisherTest {

    private final PostgresNotificationPublisher notifications = mock(PostgresNotificationPublisher.class);
    private final ProjectStreamPublisher publisher = new ProjectStreamPublisher(notifications);

    @AfterEach
    void endTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("one transaction's repeats of a mandate and kind are one notification")
    void repeatsInOneTransactionAreOneNotification() {
        UUID projectId = UUID.randomUUID();
        UUID otherProjectId = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(projectId, ProjectStreamKind.OUTREACH);
        publisher.publish(projectId, ProjectStreamKind.OUTREACH);
        publisher.publish(projectId, ProjectStreamKind.TASK);
        publisher.publish(otherProjectId, ProjectStreamKind.OUTREACH);

        verify(notifications).publish(ProjectStreamNotificationHandler.CHANNEL,
                new ProjectStreamNotification(projectId, ProjectStreamKind.OUTREACH.wire()));
        verify(notifications).publish(ProjectStreamNotificationHandler.CHANNEL,
                new ProjectStreamNotification(projectId, ProjectStreamKind.TASK.wire()));
        verify(notifications).publish(ProjectStreamNotificationHandler.CHANNEL,
                new ProjectStreamNotification(otherProjectId, ProjectStreamKind.OUTREACH.wire()));
    }

    @Test
    @DisplayName("the next transaction announces again")
    void theNextTransactionAnnouncesAgain() {
        UUID projectId = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();
        publisher.publish(projectId, ProjectStreamKind.OUTREACH);
        TransactionSynchronizationManager.clearSynchronization();

        TransactionSynchronizationManager.initSynchronization();
        publisher.publish(projectId, ProjectStreamKind.OUTREACH);

        verify(notifications, times(2)).publish(eq(ProjectStreamNotificationHandler.CHANNEL), any());
    }
}
