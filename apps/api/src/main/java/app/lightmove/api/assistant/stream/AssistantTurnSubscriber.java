package app.lightmove.api.assistant.stream;

import app.lightmove.api.assistant.constant.AssistantEventKind;
import app.lightmove.api.assistant.model.AssistantEvent;
import app.lightmove.api.assistant.repository.AssistantEventRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * One open browser connection to one turn, and its cursor.
 *
 * <p><b>Every send goes through {@link #drain}, and nothing is ever sent from a notification.</b>
 * That single rule is what makes this correct, and it is why there is no separate "replay" phase to
 * get wrong: registering sets the cursor from the client's {@code afterSeq}, and the first drain
 * <i>is</i> the replay. A {@code NOTIFY} arriving mid-replay only queues another drain, which finds
 * nothing new. A reconnect is the same field, so resume and tail are one code path.
 *
 * <p>The cursor advances only <b>after</b> a successful send. An event written but not yet delivered
 * therefore stays pending rather than being skipped, which is the difference between a stream that
 * loses an answer and one that repeats a frame.
 */
class AssistantTurnSubscriber {

    /**
     * One drain reads at most this many events. A long turn is drained in several passes rather than
     * one, so a catch-up cannot hold a connection out of a pool of five for the whole replay.
     */
    private static final int DRAIN_BATCH = 200;

    /** Compared as the stored wire string, never parsed back into the enum — see AssistantEventKind. */
    private static final String TERMINAL_KIND = AssistantEventKind.TURN_FINISHED.wire();

    private final SseEmitter emitter;
    private final UUID turnId;
    private final ObjectMapper json;

    private final ReentrantLock sending = new ReentrantLock();

    /**
     * Set while a drain is queued or running. A turn emitting a few events a second must not queue a
     * task per event per viewer — this is the server-side counterpart of {@code useProjectStream}'s
     * trailing coalesce timer.
     */
    private final AtomicBoolean drainPending = new AtomicBoolean();

    /** Guarded by {@link #sending}. Zero means nothing sent yet, so the whole turn replays. */
    private int lastSentSeq;

    private volatile boolean finished;

    AssistantTurnSubscriber(SseEmitter emitter, UUID turnId, int afterSeq, ObjectMapper json) {
        this.emitter = emitter;
        this.turnId = turnId;
        this.json = json;
        this.lastSentSeq = Math.max(0, afterSeq);
    }

    SseEmitter emitter() {
        return emitter;
    }

    UUID turnId() {
        return turnId;
    }

    /**
     * Whether a drain is worth scheduling. The one legitimate use of a notification's seq: if the
     * client is already past it there is nothing to fetch. Anything else read off the payload is a
     * bug.
     */
    boolean mightHaveMissed(int seq) {
        return !finished && seq > lastSentSeq;
    }

    /** @return false when a drain is already queued or running, so the caller schedules nothing */
    boolean claimDrain() {
        return drainPending.compareAndSet(false, true);
    }

    /** Hands a claim back when the drain could not be scheduled at all. */
    void releaseDrain() {
        drainPending.set(false);
    }

    /**
     * Sends everything after the cursor, in order, and completes the stream once the turn has
     * finished.
     *
     * @return false when the emitter died and this subscriber should be dropped
     */
    boolean drain(AssistantEventRepository events) {
        sending.lock();
        try {
            // Cleared first, so an event committing while this drain runs claims a fresh one rather
            // than waiting for the next notification to arrive.
            drainPending.set(false);
            while (true) {
                List<AssistantEvent> batch = events.findByTurnIdAndSeqGreaterThanOrderBySeqAsc(
                        turnId, lastSentSeq, PageRequest.of(0, DRAIN_BATCH));
                if (batch.isEmpty()) {
                    return true;
                }
                for (AssistantEvent event : batch) {
                    if (!send(event)) {
                        return false;
                    }
                    lastSentSeq = event.getSeq();
                    if (TERMINAL_KIND.equals(event.getKind())) {
                        // Ends the stream rather than holding the browser for the rest of the 55s
                        // window and making it reconnect to a turn that will never speak again.
                        finished = true;
                        emitter.complete();
                        return true;
                    }
                }
            }
        } finally {
            sending.unlock();
        }
    }

    /**
     * Serialised with Jackson rather than concatenated. {@code ProjectStreamRegistry} can afford
     * string building because its payload is one enum constant; an assistant payload is free text,
     * and a single quote in it would produce a frame the browser drops without a word. A newline
     * would be worse — {@code data:} is line-delimited, so a literal one splits the frame in two —
     * which is why this mapper must never be configured to pretty-print.
     */
    private boolean send(AssistantEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name("assistant")
                    .data(json.writeValueAsString(new Frame(event.getSeq(), event.getKind(),
                            event.getPayload(), event.getOccurredAt()))));
            return true;
        } catch (IOException | IllegalStateException dead) {
            return false;
        }
    }

    boolean isFinished() {
        return finished;
    }

    /** What one SSE frame carries. {@code kind} is the stored wire string, passed through unparsed. */
    private record Frame(int seq, String kind, Map<String, Object> payload, Instant occurredAt) {}
}
