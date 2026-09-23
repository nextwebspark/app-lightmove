package app.lightmove.api.assistant.dto;

import app.lightmove.api.assistant.model.AssistantThread;
import java.time.Instant;
import java.util.UUID;

/** A row of the history list. */
public record AssistantThreadSummary(UUID id, String title, Instant updatedAt) {

    public static AssistantThreadSummary of(AssistantThread thread) {
        return new AssistantThreadSummary(thread.getId(), thread.getTitle(), thread.getUpdatedAt());
    }
}
