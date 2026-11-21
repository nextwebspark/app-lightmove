package app.lightmove.api.notification.service;

import app.lightmove.api.core.config.LightMoveProperties;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Where a notification's email opens: the bell's own destinations (the SPA's `notificationHref`), built once. */
@Component
public class NotificationLinks {

    private final String baseUrl;

    @Autowired
    public NotificationLinks(LightMoveProperties properties) {
        this(properties.web().baseUrl());
    }

    private NotificationLinks(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** For a test, or anything else that knows the site's address without the whole configuration. */
    public static NotificationLinks at(String baseUrl) {
        return new NotificationLinks(baseUrl);
    }

    public String home() {
        return baseUrl + "/";
    }

    public String myTasks() {
        return baseUrl + "/tasks";
    }

    public String position(UUID projectId) {
        return "%s/projects/%s/pipeline".formatted(baseUrl, projectId);
    }

    /** A candidate's drawer on the board, else the person's record, opened on the note when there is one. */
    public String of(UUID projectId, UUID candidateId, UUID personId, UUID noteId) {
        if (projectId == null) {
            return home();
        }
        if (candidateId != null) {
            String candidate = "%s?candidate=%s".formatted(position(projectId), candidateId);
            return noteId == null ? candidate : candidate + "&note=" + noteId;
        }
        if (personId != null) {
            String person = "%s/candidates?person=%s".formatted(baseUrl, personId);
            return noteId == null ? person : person + "&tab=notes";
        }
        return position(projectId);
    }
}
