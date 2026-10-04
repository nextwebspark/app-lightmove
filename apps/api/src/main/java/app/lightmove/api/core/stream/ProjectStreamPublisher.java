package app.lightmove.api.core.stream;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Announces that a mandate's data changed; a typed front on {@link PostgresNotificationPublisher}.
 * {@code MANDATORY} is repeated here so a publish outside a transaction is refused at this bean.
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
