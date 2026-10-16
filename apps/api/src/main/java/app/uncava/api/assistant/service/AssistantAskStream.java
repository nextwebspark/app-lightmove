package app.uncava.api.assistant.service;

import app.uncava.api.assistant.dto.AssistantTurnResponse;
import app.uncava.api.assistant.model.AssistantThread;
import app.uncava.api.billing.usage.constant.UsageKind;
import app.uncava.api.billing.usage.service.FairUseGuard;
import app.uncava.api.core.config.UncavaProperties;
import app.uncava.api.core.error.constant.ErrorCode;
import app.uncava.api.core.error.model.ApiException;
import app.uncava.api.core.ratelimit.service.LlmBudget;
import app.uncava.api.core.ratelimit.service.LlmBudgetGuard;
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
 * {@code done} or {@code failed} events, after a {@code started} event naming the chat. A closed tab
 * only stops the sending; the answer is still saved.
 *
 * <p>An answer outliving the stream is reported as still answering, never failed — asking again would
 * pay twice. The executor is unbounded virtual threads, so the slots are the only concurrency cap. The
 * stream has its own, longer limit than {@code SseStreams}: it is one answer, not a feed that reconnects.
 */
@Slf4j
@Service
public class AssistantAskStream {

    private static final long STREAM_GRACE_AFTER_DEADLINE_MS = 10_000;

    private final AssistantService assistant;
    private final AsyncTaskExecutor executor;
    private final LlmBudgetGuard budget;
    private final FairUseGuard fairUse;
    private final Semaphore slots;
    private final long stillAnsweringAfterMs;

    public AssistantAskStream(AssistantService assistant,
                              @Qualifier("applicationTaskExecutor") AsyncTaskExecutor executor,
                              LlmBudgetGuard budget, FairUseGuard fairUse, UncavaProperties properties) {
        this.assistant = assistant;
        this.executor = executor;
        this.budget = budget;
        this.fairUse = fairUse;
        this.slots = new Semaphore(properties.assistant().maxConcurrentAsks());
        this.stillAnsweringAfterMs = properties.assistant().answerDeadline().toMillis();
    }

    public SseEmitter ask(UUID userId, UUID workspaceId, UUID projectId, UUID threadId, String question) {
        AssistantThread existing = assistant.requireThread(userId, workspaceId, projectId, threadId);
        budget.require(LlmBudget.ASSISTANT, userId);
        fairUse.check(workspaceId, userId, UsageKind.ASSISTANT_ASK, 1);
        if (!slots.tryAcquire()) {
            throw ApiException.of(ErrorCode.ASSISTANT_BUSY);
        }
        SseEmitter emitter = new SseEmitter(stillAnsweringAfterMs + STREAM_GRACE_AFTER_DEADLINE_MS);
        AtomicBoolean closed = new AtomicBoolean();
        CompletableFuture.delayedExecutor(stillAnsweringAfterMs, TimeUnit.MILLISECONDS).execute(() -> {
            if (closed.compareAndSet(false, true)) {
                log.info("Assistant answer still being written after {}ms; its stream is closed", stillAnsweringAfterMs);
                send(emitter, "failed", Map.of("code", ErrorCode.ASSISTANT_STILL_ANSWERING.name()));
                emitter.complete();
            }
        });
        try {
            AssistantThread founded = existing != null ? null
                    : assistant.foundThread(userId, workspaceId, projectId, question);
            AssistantThread thread = existing != null ? existing : founded;
            executor.execute(() -> answer(emitter, closed, userId, workspaceId, projectId, thread, founded, question));
        } catch (RuntimeException rejected) {
            slots.release();
            throw rejected;
        }
        return emitter;
    }

    /** {@code founded} is the chat this ask started, or null when it was asked in an existing one. */
    private void answer(SseEmitter emitter, AtomicBoolean closed, UUID userId, UUID workspaceId,
                        UUID projectId, AssistantThread thread, AssistantThread founded, String question) {
        send(emitter, "started", Map.of("threadId", thread.getId().toString()));
        try {
            AssistantTurnResponse turn = assistant.ask(userId, workspaceId, projectId, thread, question,
                    step -> send(emitter, "step", step),
                    proposal -> send(emitter, "proposal", proposal));
            finish(emitter, closed, "done", turn);
        } catch (ApiException failed) {
            discard(founded);
            finish(emitter, closed, "failed", Map.of("code", failed.getCode().name()));
        } catch (RuntimeException failed) {
            log.warn("Assistant answer failed", failed);
            discard(founded);
            finish(emitter, closed, "failed", Map.of("code", ErrorCode.INTERNAL_ERROR.name()));
        } finally {
            slots.release();
        }
    }

    private void discard(AssistantThread founded) {
        if (founded == null) {
            return;
        }
        try {
            assistant.discardIfUnanswered(founded);
        } catch (RuntimeException failed) {
            log.warn("An unanswered chat could not be removed", failed);
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
