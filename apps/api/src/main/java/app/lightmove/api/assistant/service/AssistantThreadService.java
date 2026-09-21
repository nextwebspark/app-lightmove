package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.dto.AskRequest;
import app.lightmove.api.assistant.dto.AssistantThreadResponse;
import app.lightmove.api.assistant.dto.AssistantThreadSummary;
import app.lightmove.api.assistant.dto.AssistantTurnResponse;
import app.lightmove.api.assistant.model.AssistantThread;
import app.lightmove.api.assistant.repository.AssistantThreadRepository;
import app.lightmove.api.assistant.repository.AssistantTurnRepository;
import app.lightmove.api.assistant.service.AssistantTurnStore.StartedTurn;
import app.lightmove.api.assistant.service.AssistantTurnStore.TurnOrigin;
import app.lightmove.api.assistant.service.AssistantTurnWorker.TurnWork;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.logging.service.CorrelationId;
import app.lightmove.api.core.security.service.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A user's own assistant conversations, and running one turn of one.
 *
 * <p><b>Threads are private to the person who started them.</b> Not a workspace resource with a
 * permission on it — the {@code (workspaceId, userId)} pair is the whole authorisation, so every
 * read is scoped on both and someone else's thread is answered as a 404.
 *
 * <p>{@code ask} accepts and hands off; {@link AssistantTurnWorker} runs the turn. Every write is a
 * short transaction on {@link AssistantTurnStore} or {@link AssistantEventAppender}, and the model
 * call sits inside none of them.
 *
 * <p>A turn runs with tools, and cross-project isolation is decided per tool call against that
 * call's own arguments — not here. What this class contributes is the context those arguments are
 * chosen from: the thread's mandate, named so the model can ask about it, authorising nothing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssistantThreadService {

    private static final int MAX_THREADS_LISTED = 50;

    private final AssistantThreadRepository threads;
    private final AssistantTurnRepository turns;
    private final AssistantTurnStore store;
    private final AssistantTurnWorker worker;
    private final AssistantContextComposer context;
    private final AssistantPromptAssembler prompts;
    private final ClientIpResolver clientIps;

    @Transactional(readOnly = true)
    public List<AssistantThreadSummary> list(UUID userId, UUID workspaceId) {
        return threads
                .findByWorkspaceIdAndUserIdOrderByUpdatedAtDesc(workspaceId, userId,
                        PageRequest.of(0, MAX_THREADS_LISTED))
                .stream()
                .map(AssistantThreadSummary::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public AssistantThreadResponse get(UUID threadId, UUID userId, UUID workspaceId) {
        AssistantThread thread = threads.findByIdAndWorkspaceIdAndUserId(threadId, workspaceId, userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        List<AssistantTurnResponse> answered = turns.findByThreadIdOrderByCreatedAtAsc(thread.getId())
                .stream()
                .map(AssistantTurnResponse::of)
                .toList();
        return new AssistantThreadResponse(thread.getId(), thread.getTitle(), thread.getProjectId(),
                thread.getCreatedAt(), thread.getUpdatedAt(), answered);
    }

    /**
     * Accepts one question and hands it to a worker.
     *
     * <p>Answers 202 with a RUNNING turn. The answer arrives on
     * {@code GET /api/v1/assistant/turns/{id}/stream}, or on a refetch of the thread — a turn with
     * tools runs 30–180s and no single HTTP response can hold that against a 55s stream cycle.
     *
     * <p><b>Must stay non-{@code @Transactional}</b>, now for two reasons. The model call must not
     * hold a connection, and {@code begin} must have <i>committed</i> before the worker reads the
     * turn on another thread — wrapping this method would put that race back.
     */
    public AssistantTurnResponse ask(UUID userId, UUID workspaceId, UUID threadId, AskRequest request,
                                     HttpServletRequest httpRequest) {
        String question = request.question().strip();
        TurnOrigin origin = originOf(httpRequest);
        StartedTurn started = store.begin(userId, workspaceId, threadId, request.projectId(),
                question, origin);

        // The thread's mandate rather than the request's: a continued thread keeps what it was
        // started about, and store.begin has already decided which that is.
        String systemPrompt = prompts.assemble(
                context.compose(userId, workspaceId, started.projectId()));

        try {
            worker.run(new TurnWork(started.turnId(), started.threadId(), userId, workspaceId,
                    systemPrompt, started.history(), question,
                    origin.ipAddress(), origin.userAgent()), origin.correlationId());
        } catch (TaskRejectedException full) {
            // Visible here precisely because the hand-off is a direct @Async call rather than an
            // after-commit event: a rejection buried in a transaction callback would leave this turn
            // RUNNING for ever. Settled honestly instead, so the row says what happened.
            log.warn("Refused assistant turn {}: every slot is taken", started.turnId());
            store.fail(started.turnId(), ErrorCode.ASSISTANT_BUSY);
            throw ApiException.of(ErrorCode.ASSISTANT_BUSY);
        }

        return started.accepted();
    }

    /**
     * Resolved here, on the request thread, because the worker that will run turns after #427 has
     * no {@code HttpServletRequest} — Tomcat recycles it — and {@code CorrelationId.current()}
     * answers "none" off one.
     */
    private TurnOrigin originOf(HttpServletRequest httpRequest) {
        return new TurnOrigin(clientIps.resolve(httpRequest),
                httpRequest.getHeader("User-Agent"), CorrelationId.current());
    }
}
