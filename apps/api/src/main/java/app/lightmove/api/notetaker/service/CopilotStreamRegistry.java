package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.stream.SseStreams;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The copilot windows this instance holds, keyed by recording. A nudge carries no content: the window reads the
 * copilot back through the guarded read, after the last line it holds.
 */
@Component
public class CopilotStreamRegistry {

    private static final String CHANGE = "{\"kind\":\"copilot\"}";

    private final Map<UUID, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID recordingId) {
        SseEmitter emitter = new SseEmitter(SseStreams.STREAM_TIMEOUT_MS);
        streams.computeIfAbsent(recordingId, id -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> drop(recordingId, emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> drop(recordingId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("{}"));
        } catch (IOException | IllegalStateException dead) {
            drop(recordingId, emitter);
        }
        return emitter;
    }

    public void nudge(UUID recordingId) {
        Set<SseEmitter> held = streams.get(recordingId);
        if (held == null) {
            return;
        }
        for (SseEmitter emitter : held) {
            try {
                emitter.send(SseEmitter.event().name("change").data(CHANGE));
            } catch (IOException | IllegalStateException dead) {
                drop(recordingId, emitter);
            }
        }
    }

    private void drop(UUID recordingId, SseEmitter emitter) {
        streams.computeIfPresent(recordingId, (id, held) -> {
            held.remove(emitter);
            return held.isEmpty() ? null : held;
        });
    }
}
