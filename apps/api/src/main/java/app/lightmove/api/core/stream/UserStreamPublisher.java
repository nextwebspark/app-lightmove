package app.lightmove.api.core.stream;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Announces that a person's notifications changed in one workspace, at the commit that changed them. */
@Component
@RequiredArgsConstructor
public class UserStreamPublisher {

    private final PostgresNotificationPublisher notifications;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(UUID userId, UUID workspaceId) {
        notifications.publish(UserStreamNotificationHandler.CHANNEL, new UserStreamNotification(userId, workspaceId));
    }
}
