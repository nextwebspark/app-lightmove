package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.constant.AssistantEventKind;
import app.lightmove.api.assistant.model.AssistantEvent;
import app.lightmove.api.assistant.repository.AssistantEventRepository;
import app.lightmove.api.assistant.stream.AssistantTurnNotification;
import app.lightmove.api.core.stream.PostgresNotificationPublisher;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends one entry to a turn's log and wakes whoever is streaming it.
 *
 * <p><b>Its own bean because it must always be reached across the proxy.</b> An append called from
 * another method of the calling class would run with no transaction at all — the
 * {@code AuditEventWriter} trap, which this codebase has already paid for once.
 *
 * <p>{@code REQUIRED} rather than {@code REQUIRES_NEW}, and both halves matter. The accept path calls
 * this from inside {@code AssistantTurnStore.begin}'s transaction and needs the turn row, its first
 * event and the {@code NOTIFY} to commit together; {@code REQUIRES_NEW} would split them and hold a
 * second connection while the first was still open, which against {@code DB_POOL_MAX} of 5 is not a
 * theoretical cost. The worker calls it with nothing open, and {@code REQUIRED} starts a transaction
 * there — which is also what makes the {@code pg_notify} ride a commit rather than fire early.
 */
@Component
@RequiredArgsConstructor
public class AssistantEventAppender {

    private final AssistantEventRepository events;
    private final PostgresNotificationPublisher notifications;

    /**
     * Writes the next event and announces it.
     *
     * <p>{@code max(seq) + 1} is safe because a turn has exactly one writer <b>at a time</b>: accept
     * writes seq 1 and commits, and the worker owns the turn from then on. Since the tool surface
     * landed that is no longer one thread — answer text is drained on the worker while a tool's own
     * events are emitted from inside the advisor's chain — so the worker serialises its own sink
     * (see {@code AssistantTurnWorker.sinkFor}) rather than relying on the two never overlapping. Two writers racing would
     * both read the same max and the second insert would die on V65's
     * {@code app_lm_assistant_event_seq_uk} — <b>which is the correct outcome.</b> A retry or an
     * {@code ON CONFLICT} would silently renumber, and {@code seq} is the replay cursor, so
     * renumbering reorders the conversation for every reader still catching up.
     *
     * <p>A caller that genuinely races — the stranded-turn sweep is the only legitimate one, against a
     * worker still alive on the other instance — must call this from <b>outside</b> a transaction and
     * catch the violation itself. Catching it inside one buys nothing: the failed insert has already
     * marked that transaction rollback-only, so the commit throws
     * {@code UnexpectedRollbackException} anyway. This method deliberately offers no quiet variant,
     * because a quiet variant is only correct at a transaction boundary its caller owns.
     *
     * @return the seq allocated, which is the cursor value a client will send back as {@code afterSeq}
     */
    @Transactional
    public int append(UUID turnId, AssistantEventKind kind, Map<String, Object> payload) {
        int next = events.maxSeq(turnId) + 1;
        events.save(AssistantEvent.of(turnId, next, kind, payload));
        notifications.publish(AssistantTurnNotification.CHANNEL,
                new AssistantTurnNotification(turnId, next));
        return next;
    }
}
