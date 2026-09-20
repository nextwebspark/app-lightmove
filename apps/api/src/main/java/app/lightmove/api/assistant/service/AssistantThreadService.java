package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.dto.AskRequest;
import app.lightmove.api.assistant.dto.AssistantThreadResponse;
import app.lightmove.api.assistant.dto.AssistantThreadSummary;
import app.lightmove.api.assistant.dto.AssistantTurnResponse;
import app.lightmove.api.assistant.model.AssistantAnswer;
import app.lightmove.api.assistant.model.AssistantThread;
import app.lightmove.api.assistant.model.AssistantTurn;
import app.lightmove.api.assistant.model.AssistantTurnPrompt;
import app.lightmove.api.assistant.repository.AssistantThreadRepository;
import app.lightmove.api.assistant.repository.AssistantTurnRepository;
import app.lightmove.api.assistant.service.AssistantTurnStore.StartedTurn;
import app.lightmove.api.assistant.service.AssistantTurnStore.TurnOrigin;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.logging.service.CorrelationId;
import app.lightmove.api.core.security.service.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * <p>{@code ask} is deliberately <b>not</b> {@code @Transactional}: the model call must not hold a
 * database connection, so the writes either side of it are two short transactions on
 * {@link AssistantTurnStore}. It also runs the turn <b>with no tools</b> — nothing here can reach a
 * mandate's data, which is what makes cross-project isolation a non-question until #425 introduces
 * the tool surface and the guard that authorises each call against its own arguments.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssistantThreadService {

    /**
     * Placeholder until #428 assembles a real one. It says what the assistant is and nothing about
     * the caller or the mandate, because with no tools there is nothing it could usefully be told.
     */
    private static final String SYSTEM_PROMPT = """
            You are Uncava's research assistant, helping an executive search consultant.
            Answer concisely. You have no access to the firm's data yet, so if a question needs it,
            say plainly that you cannot look it up rather than guessing at names or figures.
            """;

    private static final int MAX_THREADS_LISTED = 50;

    private final AssistantThreadRepository threads;
    private final AssistantTurnRepository turns;
    private final AssistantTurnStore store;
    private final AssistantTurnRunner runner;
    private final ClientIpResolver clientIps;
    private final AuditService audit;

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
     * Asks one question and waits for the answer.
     *
     * <p>Synchronous on purpose, and only for now: a turn with tools will outlive the 55s the SSE
     * cycle allows, which is what #427 replaces this with. Until then the shape is worth having —
     * the turn is recorded before the model is called and settled after it, so a turn that fails
     * still leaves the question on the record rather than vanishing.
     */
    public AssistantTurnResponse ask(UUID userId, UUID workspaceId, UUID threadId, AskRequest request,
                                     HttpServletRequest httpRequest) {
        StartedTurn started = store.begin(userId, workspaceId, threadId, request.projectId(),
                request.question().strip(), originOf(httpRequest));

        AssistantTurn settled = runAndSettle(started, request.question().strip());

        audit.event(WorkspaceEventType.ASSISTANT_TURN_RAN)
                .actor(userId)
                .workspace(workspaceId)
                .target("assistantThread", started.threadId())
                .from(httpRequest)
                .detail("turnId", started.turnId())
                .detail("status", settled.getStatus().name())
                // All three are absent on a failed turn, and the token counts are absent on a
                // successful one the provider did not meter. detail() would seal a null into the
                // map and throw out of a request whose answer is already stored.
                .detailIfPresent("model", settled.getModel())
                .detailIfPresent("inputTokens", settled.getInputTokens())
                .detailIfPresent("outputTokens", settled.getOutputTokens())
                .record();

        return AssistantTurnResponse.of(settled);
    }

    /**
     * The model call, outside any transaction, with both endings written.
     *
     * <p>A broad catch because anything escaping here would leave the turn RUNNING for ever — the
     * SPA would poll a turn that is never going to finish, and V65's partial index on RUNNING turns
     * would fill with rows no sweep could distinguish from live ones.
     */
    private AssistantTurn runAndSettle(StartedTurn started, String question) {
        try {
            AssistantAnswer answer = runner.run(
                    new AssistantTurnPrompt(SYSTEM_PROMPT, started.history(), question));
            return store.succeed(started.turnId(), answer);
        } catch (ApiException failed) {
            log.warn("Assistant turn {} failed: {}", started.turnId(), failed.getCode());
            return store.fail(started.turnId(), failed.getCode());
        } catch (RuntimeException failed) {
            log.error("Assistant turn {} failed", started.turnId(), failed);
            return store.fail(started.turnId(), ErrorCode.INTERNAL_ERROR);
        }
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
