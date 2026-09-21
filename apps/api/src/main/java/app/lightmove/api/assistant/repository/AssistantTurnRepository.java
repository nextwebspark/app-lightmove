package app.lightmove.api.assistant.repository;

import app.lightmove.api.assistant.constant.AssistantTurnStatus;
import app.lightmove.api.assistant.model.AssistantTurn;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Turns are always reached through their thread, and the thread is what carries the ownership check
 * — so these finders scope on {@code threadId} and the caller proves the thread is theirs first.
 */
public interface AssistantTurnRepository extends JpaRepository<AssistantTurn, UUID> {

    List<AssistantTurn> findByThreadIdOrderByCreatedAtAsc(UUID threadId);

    /**
     * Whether this turn is the caller's own, answered from the turn row alone.
     *
     * <p>One query and no join: V65 puts {@code workspace_id} and {@code actor_user_id} on the turn
     * itself so a background worker can authorise without a request, and a stream opening on a turn
     * gets the same check for free. An {@code exists} rather than a fetch because nothing but the
     * answer to that question is wanted.
     */
    boolean existsByIdAndWorkspaceIdAndActorUserId(UUID id, UUID workspaceId, UUID actorUserId);

    /**
     * The same check, when the caller also needs the turn's status.
     *
     * <p>One read rather than an {@code exists} and a fetch: accepting a proposal has to know both
     * whether the turn is the caller's and whether it has stopped writing, and two queries could
     * disagree across the gap.
     */
    Optional<AssistantTurn> findByIdAndWorkspaceIdAndActorUserId(UUID id, UUID workspaceId,
                                                                 UUID actorUserId);

    /**
     * Whether this thread already has a turn in flight. Served by V65's
     * {@code app_lm_assistant_turn_thread_idx}, and checked inside the accept transaction so two
     * requests racing cannot both pass it.
     */
    boolean existsByThreadIdAndStatus(UUID threadId, AssistantTurnStatus status);

    /**
     * Ids of turns still running since before a cut-off — the sweep's input.
     *
     * <p>Ids rather than entities, and served by V65's partial
     * {@code app_lm_assistant_turn_running_idx}: each one is then settled in its own short
     * transaction, so a long batch never holds one open.
     */
    @Query("select t.id from AssistantTurn t where t.status = :status and t.startedAt < :before"
            + " order by t.startedAt asc")
    List<UUID> findIdsByStatusAndStartedAtBefore(@Param("status") AssistantTurnStatus status,
                                                 @Param("before") Instant before, Pageable page);
}
