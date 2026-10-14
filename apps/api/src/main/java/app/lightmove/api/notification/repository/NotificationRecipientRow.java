package app.lightmove.api.notification.repository;

import java.util.UUID;

/** A workspace and a person in it. */
public interface NotificationRecipientRow {

    UUID getWorkspaceId();

    UUID getUserId();
}
