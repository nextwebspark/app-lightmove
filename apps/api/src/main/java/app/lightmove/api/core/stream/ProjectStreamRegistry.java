package app.lightmove.api.core.stream;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The emitters this instance holds, keyed by project. Purely local: the other instance's emitters are
 * reached through Postgres (see the package doc), so a broadcast here only ever fans out to the
 * browsers connected to this JVM.
 */
@Component
@Slf4j
public class ProjectStreamRegistry {

    /**
     * Just under Cloud Run's 60s request timeout, so the server ends every stream cleanly and the
     * browser reconnects on a normal close instead of a mid-air network error.
     */
    public static final long STREAM_TIMEOUT_MS = 55_000;

    private final Map<UUID, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID projectId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        streams.computeIfAbsent(projectId, id -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> drop(projectId, emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> drop(projectId, emitter));
        try {
            // A first event straight away, so the response headers flush and the client knows the
            // stream is live rather than buffered somewhere along the way.
            emitter.send(SseEmitter.event().name("connected").data("{}"));
        } catch (IOException | IllegalStateException dead) {
            drop(projectId, emitter);
        }
        return emitter;
    }

    public void broadcast(UUID projectId, String kind) {
        Set<SseEmitter> held = streams.get(projectId);
        if (held == null) {
            return;
        }
        for (SseEmitter emitter : held) {
            try {
                emitter.send(SseEmitter.event().name("change").data("{\"kind\":\"" + kind + "\"}"));
            } catch (IOException | IllegalStateException dead) {
                drop(projectId, emitter);
            }
        }
    }

    private void drop(UUID projectId, SseEmitter emitter) {
        streams.computeIfPresent(projectId, (id, held) -> {
            held.remove(emitter);
            return held.isEmpty() ? null : held;
        });
    }
}
