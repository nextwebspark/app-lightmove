package app.lightmove.api.assistant.stream;

import app.lightmove.api.assistant.repository.AssistantEventRepository;
import app.lightmove.api.core.stream.SseStreams;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * The turn streams this instance holds. Purely local — the other instance's subscribers are reached
 * through Postgres, so a wake here only ever fans out to the browsers connected to this JVM.
 *
 * <p>Two things it does that {@code ProjectStreamRegistry} does not, both because an assistant event
 * is content rather than a "refetch" hint:
 *
 * <ul>
 *   <li><b>Drains off the listener thread.</b> A drain does a database read and a socket write per
 *       subscriber. Doing that on the single {@code LISTEN} thread would let one slow client stall
 *       every stream on the instance — including the project grid's.
 *   <li><b>Coalesces per subscriber.</b> A turn streaming text emits a few events a second; without
 *       the {@code drainPending} claim that would be a task per event per viewer.
 * </ul>
 */
@Component
@Slf4j
public class AssistantTurnStreamRegistry {

    private final Map<UUID, Set<AssistantTurnSubscriber>> streams = new ConcurrentHashMap<>();

    private final AssistantEventRepository events;
    private final Executor drains;
    private final ObjectMapper json;

    public AssistantTurnStreamRegistry(AssistantEventRepository events,
                                       @Qualifier("assistantStreamExecutor") Executor drains,
                                       ObjectMapper json) {
        this.events = events;
        this.drains = drains;
        this.json = json;
    }

    /**
     * Registers a subscriber at the client's cursor and sends it everything it has missed.
     *
     * <p>Registration happens <b>before</b> the first read, which is what makes "subscribe before you
     * replay" structural rather than a discipline: an event committing between the two is picked up
     * by the drain the notification schedules, because both go through the same cursor.
     */
    public SseEmitter subscribe(UUID turnId, int afterSeq) {
        SseEmitter emitter = new SseEmitter(SseStreams.STREAM_TIMEOUT_MS);
        AssistantTurnSubscriber subscriber =
                new AssistantTurnSubscriber(emitter, turnId, afterSeq, json);

        streams.computeIfAbsent(turnId, id -> ConcurrentHashMap.newKeySet()).add(subscriber);
        emitter.onCompletion(() -> drop(subscriber));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> drop(subscriber));

        try {
            // Flushes the response headers, so the client knows the stream is live rather than
            // buffered somewhere along the way. The SPA's reconnect logic keys on seeing this.
            emitter.send(SseEmitter.event().name("connected").data("{}"));
        } catch (IOException | IllegalStateException dead) {
            drop(subscriber);
            return emitter;
        }

        schedule(subscriber);
        return emitter;
    }

    /** A turn moved. Called from the {@code LISTEN} thread, so it must not do the work itself. */
    public void wake(UUID turnId, int seq) {
        Set<AssistantTurnSubscriber> held = streams.get(turnId);
        if (held == null) {
            return;
        }
        for (AssistantTurnSubscriber subscriber : held) {
            if (subscriber.mightHaveMissed(seq)) {
                schedule(subscriber);
            }
        }
    }

    private void schedule(AssistantTurnSubscriber subscriber) {
        if (!subscriber.claimDrain()) {
            return;
        }
        try {
            drains.execute(() -> {
                if (!subscriber.drain(events) || subscriber.isFinished()) {
                    drop(subscriber);
                }
            });
        } catch (RuntimeException rejected) {
            // Release the claim, or this subscriber never schedules again and sits silent until its
            // ~55s reconnect. A dropped drain is not a lost event — the next append schedules
            // another, and a reconnect replays from the cursor regardless.
            subscriber.releaseDrain();
            log.warn("Dropped a drain for turn {}", subscriber.turnId(), rejected);
        }
    }

    private void drop(AssistantTurnSubscriber subscriber) {
        streams.computeIfPresent(subscriber.turnId(), (id, held) -> {
            held.remove(subscriber);
            return held.isEmpty() ? null : held;
        });
    }
}
