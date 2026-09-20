package app.lightmove.api.assistant.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One conversation, owned by the person who started it.
 *
 * <p><b>The first unconditionally private row in this schema.</b> Every other tenant table is shared
 * across a workspace or a project seat; this one is not, and the {@code (workspaceId, userId)} pair
 * is the whole authorisation — there is no action above it. {@code StrategySearch} is the nearest
 * precedent, but a search is only <i>optionally</i> private and still sits under {@code PROJECT_EDIT}.
 *
 * <p>{@code projectId} is the mandate the thread was asked <i>about</i>, not its owner: it is context
 * for the model and never a substitute for authorising a tool call, which is checked against the
 * arguments of that call.
 */
@Entity
@Table(name = "app_lm_assistant_thread")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssistantThread extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Null once the mandate is deleted — V65 unmoors the thread rather than deleting it. */
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

    /**
     * Someone else's thread does not exist as far as they are concerned.
     *
     * <p>Answered as a 404 rather than a 403 for {@code StrategySearch}'s reason: telling a colleague
     * that a row exists is precisely what a private tier prevents.
     */
    public boolean isHiddenFrom(UUID readerId) {
        return !userId.equals(readerId);
    }
}
