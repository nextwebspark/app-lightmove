package app.lightmove.api.assistant.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One chat, private to the person who started it and asked inside one project. */
@Entity
@Table(name = "app_lm_assistant_thread")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssistantThread extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "title", nullable = false)
    private String title;

    public static AssistantThread of(UUID workspaceId, UUID userId, UUID projectId, String title) {
        AssistantThread thread = new AssistantThread();
        thread.workspaceId = workspaceId;
        thread.userId = userId;
        thread.projectId = projectId;
        thread.title = title;
        return thread;
    }
}
