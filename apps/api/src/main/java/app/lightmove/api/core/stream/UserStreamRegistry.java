package app.lightmove.api.core.stream;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The emitters this instance holds for people's own streams, keyed by person and workspace: a session is in
 * one workspace, so a nudge about another never reaches a tab working in this one.
 */
@Component
public class UserStreamRegistry {

    private static final String CHANGE = "{\"kind\":\"notifications\"}";

    private final Map<UserStreamKey, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId, UUID workspaceId) {
        UserStreamKey key = new UserStreamKey(userId, workspaceId);
        SseEmitter emitter = new SseEmitter(SseStreams.STREAM_TIMEOUT_MS);
        streams.computeIfAbsent(key, held -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> drop(key, emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> drop(key, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("{}"));
        } catch (IOException | IllegalStateException dead) {
            drop(key, emitter);
        }
        return emitter;
    }

    public void nudge(UUID userId, UUID workspaceId) {
        UserStreamKey key = new UserStreamKey(userId, workspaceId);
        Set<SseEmitter> held = streams.get(key);
        if (held == null) {
            return;
        }
        for (SseEmitter emitter : held) {
            try {
                emitter.send(SseEmitter.event().name("change").data(CHANGE));
            } catch (IOException | IllegalStateException dead) {
                drop(key, emitter);
            }
        }
    }

    private void drop(UserStreamKey key, SseEmitter emitter) {
        streams.computeIfPresent(key, (id, held) -> {
            held.remove(emitter);
            return held.isEmpty() ? null : held;
        });
    }

    private record UserStreamKey(UUID userId, UUID workspaceId) {}
}
