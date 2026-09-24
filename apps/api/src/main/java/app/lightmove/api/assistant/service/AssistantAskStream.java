package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.dto.AssistantTurnResponse;
import app.lightmove.api.assistant.model.AssistantThread;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.stream.SseStreams;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Answers a question while streaming what the tools are doing: a {@code step} event per step as it
 * starts and finishes, {@code proposal} with the company card the moment it is made — the answer
 * text takes one more model round after it — then {@code done} with the saved turn, or
 * {@code failed} with an error code.
 *
 * <p>The answer is worked out on a background thread so the response can flush as it goes. A
 * closed tab only stops the sending: the answer is still saved and appears in the chat history.
 */
@Slf4j
@Service
public class AssistantAskStream {

    private final AssistantService assistant;
    private final AsyncTaskExecutor executor;

    public AssistantAskStream(AssistantService assistant,
                              @Qualifier("applicationTaskExecutor") AsyncTaskExecutor executor) {
        this.assistant = assistant;
        this.executor = executor;
    }

    public SseEmitter ask(UUID userId, UUID workspaceId, UUID projectId, UUID threadId, String question) {
        AssistantThread existing = assistant.requireThread(userId, workspaceId, projectId, threadId);
        SseEmitter emitter = new SseEmitter(SseStreams.STREAM_TIMEOUT_MS);
        executor.execute(() -> {
            try {
                AssistantTurnResponse turn = assistant.ask(userId, workspaceId, projectId, existing, question,
                        step -> send(emitter, "step", step),
                        proposal -> send(emitter, "proposal", proposal));
                send(emitter, "done", turn);
            } catch (ApiException failed) {
                send(emitter, "failed", Map.of("code", failed.getCode().name()));
            } catch (RuntimeException failed) {
                log.warn("Assistant answer failed", failed);
                send(emitter, "failed", Map.of("code", ErrorCode.INTERNAL_ERROR.name()));
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    private static void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException gone) {
            log.debug("Assistant stream closed before '{}' could be sent", name);
        }
    }
}
