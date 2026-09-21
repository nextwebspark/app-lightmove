package app.lightmove.api.assistant.repository;

import app.lightmove.api.assistant.model.AssistantEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * A turn's log, read only ever forward from a cursor.
 *
 * <p>No tenant scoping here and that is deliberate: an event belongs to a turn, and the turn row is
 * what carries {@code workspace_id} and {@code actor_user_id}. The caller proves the turn is theirs
 * first — {@code AssistantTurnStreamService} does — exactly as {@code AssistantTurnRepository}
 * scopes on {@code threadId} and lets the thread carry the ownership check.
 */
public interface AssistantEventRepository extends JpaRepository<AssistantEvent, Long> {

    /**
     * Everything after the cursor, oldest first.
     *
     * <p>{@code Pageable} is the cap, not a page: the return type is a {@code List}, so no count
     * query is issued — that is only {@code Page<T>}. One drain must not hold a connection open
     * reading a whole long turn when {@code DB_POOL_MAX} is 5, so the caller passes a bounded page
     * and loops.
     *
     * <p>{@code seq} starts at 1, so {@code afterSeq = 0} replays the turn from the beginning and
     * needs no special case.
     */
    List<AssistantEvent> findByTurnIdAndSeqGreaterThanOrderBySeqAsc(UUID turnId, int afterSeq,
                                                                    Pageable page);

    /**
     * One turn's events of the given kinds, oldest first.
     *
     * <p>Kinds as wire strings because that is what the column stores — {@link AssistantEvent} keeps
     * the {@code kind} unparsed so the stream can hand it to the browser verbatim, and a finder that
     * took the enum would have to reintroduce the parsing that decision exists to avoid.
     */
    List<AssistantEvent> findByTurnIdAndKindInOrderBySeqAsc(UUID turnId, List<String> kinds);

    /** The same across a thread's turns in one query, so rendering a thread is not a query per turn. */
    List<AssistantEvent> findByTurnIdInAndKindInOrderBySeqAsc(List<UUID> turnIds, List<String> kinds);

    /** The last seq allocated for this turn, or 0 when it has none yet. */
    @Query("select coalesce(max(e.seq), 0) from AssistantEvent e where e.turnId = :turnId")
    int maxSeq(@Param("turnId") UUID turnId);
}
