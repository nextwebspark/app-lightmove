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

/**
 * Every finder carries both the workspace <b>and</b> the user — a thread is one person's, so an
 * unscoped lookup on it must not exist, and neither must one scoped only to the tenant.
 */
public interface AssistantThreadRepository extends JpaRepository<AssistantThread, UUID> {

    List<AssistantThread> findByWorkspaceIdAndUserIdOrderByUpdatedAtDesc(UUID workspaceId, UUID userId,
                                                                        Pageable pageable);

    Optional<AssistantThread> findByIdAndWorkspaceIdAndUserId(UUID id, UUID workspaceId, UUID userId);

    /**
     * Moves the thread to the top of its owner's history.
     *
     * <p>A turn is a child row, so appending one leaves the thread itself untouched — Hibernate sees
     * nothing dirty, no UPDATE is issued, and neither {@code @UpdateTimestamp} nor the table's touch
     * trigger fires. Without this the list would order by the moment each thread was *created* while
     * claiming to show the most recently used. Writing the column directly is safe: the trigger sets
     * the same value on the same statement.
     */
    @Modifying
    @Query("update AssistantThread t set t.updatedAt = :at where t.id = :id")
    void touch(@Param("id") UUID id, @Param("at") Instant at);
}
