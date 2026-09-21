package app.lightmove.api.assistant.dto;

import app.lightmove.api.assistant.model.AssistantThread;
import java.time.Instant;
import java.util.UUID;

/** A row of the history list. {@code updatedAt} is last activity, which is what it is ordered on. */
public record AssistantThreadSummary(UUID id, String title, UUID projectId, Instant updatedAt) {

    public static AssistantThreadSummary of(AssistantThread thread) {
        return new AssistantThreadSummary(thread.getId(), thread.getTitle(),
                thread.getProjectId(), thread.getUpdatedAt());
    }
}
