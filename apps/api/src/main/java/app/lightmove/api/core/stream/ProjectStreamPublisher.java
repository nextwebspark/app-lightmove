package app.lightmove.api.core.stream;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Announces that a mandate's data changed. A typed front on
 * {@link PostgresNotificationPublisher}, which holds the payload limit and the reasoning for
 * {@code MANDATORY}; the kind is an enum here so a publish site cannot invent a misspelt one.
 *
 * <p>{@code MANDATORY} is repeated rather than left to the delegate so that a publish outside a
 * transaction is refused at the bean the caller actually named — which is what
 * {@code ProjectStreamIntegrationTest.aPublishOutsideATransactionIsRefused} asserts.
 */
@Component
@RequiredArgsConstructor
public class ProjectStreamPublisher {

    private final PostgresNotificationPublisher notifications;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(UUID projectId, ProjectStreamKind kind) {
        notifications.publish(ProjectStreamNotificationHandler.CHANNEL,
                new ProjectStreamNotification(projectId, kind.wire()));
    }
}
