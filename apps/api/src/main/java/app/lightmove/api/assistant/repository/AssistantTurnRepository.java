package app.lightmove.api.assistant.repository;

import app.lightmove.api.assistant.model.AssistantTurn;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Reached through a thread the caller owns, or by id scoped to the caller directly. */
public interface AssistantTurnRepository extends JpaRepository<AssistantTurn, UUID> {

    List<AssistantTurn> findByThreadIdOrderByCreatedAtAsc(UUID threadId);

    Optional<AssistantTurn> findByIdAndWorkspaceIdAndActorUserId(UUID id, UUID workspaceId, UUID actorUserId);

    /** The turn locked for the rest of the transaction, so two accepts of one card run one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AssistantTurn> findForUpdateByIdAndWorkspaceIdAndActorUserId(UUID id, UUID workspaceId,
                                                                        UUID actorUserId);
}
