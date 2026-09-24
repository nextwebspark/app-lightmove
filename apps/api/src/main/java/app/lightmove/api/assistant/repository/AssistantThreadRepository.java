package app.lightmove.api.assistant.repository;

import app.lightmove.api.assistant.model.AssistantThread;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every finder carries the workspace and the user: a chat is one person's. */
public interface AssistantThreadRepository extends JpaRepository<AssistantThread, UUID> {

    List<AssistantThread> findByWorkspaceIdAndUserIdAndProjectIdOrderByUpdatedAtDesc(
            UUID workspaceId, UUID userId, UUID projectId, Pageable pageable);

    Optional<AssistantThread> findByIdAndWorkspaceIdAndUserId(UUID id, UUID workspaceId, UUID userId);

    /** Adding a turn leaves the thread row clean, so nothing else would move it up the history list. */
    @Modifying
    @Query("update AssistantThread t set t.updatedAt = :at where t.id = :id")
    void touch(@Param("id") UUID id, @Param("at") Instant at);
}
