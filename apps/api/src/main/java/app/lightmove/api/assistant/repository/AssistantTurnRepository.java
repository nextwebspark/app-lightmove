package app.lightmove.api.assistant.repository;

import app.lightmove.api.assistant.model.AssistantTurn;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Turns are always reached through their thread, and the thread is what carries the ownership check
 * — so these finders scope on {@code threadId} and the caller proves the thread is theirs first.
 */
public interface AssistantTurnRepository extends JpaRepository<AssistantTurn, UUID> {

    List<AssistantTurn> findByThreadIdOrderByCreatedAtAsc(UUID threadId);
}
