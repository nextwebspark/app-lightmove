package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.dto.AssistantTurnResponse;
import app.lightmove.api.assistant.model.AssistantThread;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.core.stream.SseStreams;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Answers a question on a background thread while streaming {@code step}, {@code proposal}, then
 * {@code done} or {@code failed} events. A closed tab only stops the sending; the answer is still saved.
 *
 * <p>An answer outliving the stream is reported as still answering, never failed — asking again would
 * pay twice. The executor is unbounded virtual threads, so the slots are the only concurrency cap.
 */
@Slf4j
@Service
public class AssistantAskStream {

    private static final long STILL_ANSWERING_AFTER_MS = SseStreams.STREAM_TIMEOUT_MS - 5_000;

    private final AssistantService assistant;
    private final AsyncTaskExecutor executor;
    private final LlmBudgetGuard budget;
    private final Semaphore slots;

    public AssistantAskStream(AssistantService assistant,
                              @Qualifier("applicationTaskExecutor") AsyncTaskExecutor executor,
                              LlmBudgetGuard budget, LightMoveProperties properties) {
        this.assistant = assistant;
        this.executor = executor;
        this.budget = budget;
        this.slots = new Semaphore(properties.assistant().maxConcurrentAsks());
    }

    public SseEmitter ask(UUID userId, UUID workspaceId, UUID projectId, UUID threadId, String question) {
        AssistantThread existing = assistant.requireThread(userId, workspaceId, projectId, threadId);
        budget.require(LlmBudget.ASSISTANT, userId);
        if (!slots.tryAcquire()) {
            throw ApiException.of(ErrorCode.ASSISTANT_BUSY);
        }
        SseEmitter emitter = new SseEmitter(SseStreams.STREAM_TIMEOUT_MS);
        AtomicBoolean closed = new AtomicBoolean();
        CompletableFuture.delayedExecutor(STILL_ANSWERING_AFTER_MS, TimeUnit.MILLISECONDS).execute(() -> {
            if (closed.compareAndSet(false, true)) {
                send(emitter, "failed", Map.of("code", ErrorCode.ASSISTANT_STILL_ANSWERING.name()));
                emitter.complete();
            }
        });
        try {
            executor.execute(() -> answer(emitter, closed, userId, workspaceId, projectId, existing, question));
        } catch (RuntimeException rejected) {
            slots.release();
            throw rejected;
        }
        return emitter;
    }

    private void answer(SseEmitter emitter, AtomicBoolean closed, UUID userId, UUID workspaceId,
                        UUID projectId, AssistantThread existing, String question) {
        try {
            AssistantTurnResponse turn = assistant.ask(userId, workspaceId, projectId, existing, question,
                    step -> send(emitter, "step", step),
                    proposal -> send(emitter, "proposal", proposal));
            finish(emitter, closed, "done", turn);
        } catch (ApiException failed) {
            finish(emitter, closed, "failed", Map.of("code", failed.getCode().name()));
        } catch (RuntimeException failed) {
            log.warn("Assistant answer failed", failed);
            finish(emitter, closed, "failed", Map.of("code", ErrorCode.INTERNAL_ERROR.name()));
        } finally {
            slots.release();
        }
    }

    /** The last event, unless the stream already closed as still answering. */
    private static void finish(SseEmitter emitter, AtomicBoolean closed, String name, Object data) {
        if (closed.compareAndSet(false, true)) {
            send(emitter, name, data);
            emitter.complete();
        }
    }

    private static void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException gone) {
            log.debug("Assistant stream closed before '{}' could be sent", name);
        }
    }
}
