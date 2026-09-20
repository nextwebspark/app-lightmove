package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.AssistantAnswer;
import app.lightmove.api.assistant.model.AssistantExchange;
import app.lightmove.api.assistant.model.AssistantThread;
import app.lightmove.api.assistant.model.AssistantTurn;
import app.lightmove.api.assistant.repository.AssistantThreadRepository;
import app.lightmove.api.assistant.repository.AssistantTurnRepository;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two short transactions a turn is written in, with the model call in between and inside
 * neither.
 *
 * <p>Its own bean for {@code GeocodedPlaceStore}'s reason, and the pool makes it non-negotiable
 * here: {@code DB_POOL_MAX} is 5, so a handful of turns each holding a connection across a
 * multi-second Vertex call would deadlock the instance. A {@code @Transactional} method called from
 * a sibling method of the same class is inert anyway — the proxy is never crossed — which is the
 * trap {@code AuditEventWriter} exists to avoid.
 */
@Component
@RequiredArgsConstructor
public class AssistantTurnStore {

    /** Enough of the question to recognise the thread in a list; the full text is on the turn. */
    private static final int TITLE_LENGTH = 80;

    private final AssistantThreadRepository threads;
    private final AssistantTurnRepository turns;

    /**
     * Opens a turn: resolves or creates its thread, reads what has been said so far, and writes the
     * RUNNING row.
     *
     * <p>History is read <b>before</b> the new turn is saved. Read after, the in-flight question
     * would come back as part of its own history and be sent to the model twice.
     */
    @Transactional
    public StartedTurn begin(UUID userId, UUID workspaceId, UUID threadId, UUID projectId,
                             String question, TurnOrigin origin) {
        AssistantThread thread = threadId == null
                ? threads.save(AssistantThread.of(workspaceId, userId, projectId, titleFrom(question)))
                : requireOwnThread(threadId, workspaceId, userId);

        List<AssistantExchange> history = turns.findByThreadIdOrderByCreatedAtAsc(thread.getId()).stream()
                .map(turn -> new AssistantExchange(turn.getQuestion(), turn.getAnswer()))
                .toList();

        AssistantTurn turn = turns.save(AssistantTurn.running(thread.getId(), userId, workspaceId, question,
                origin.ipAddress(), origin.userAgent(), origin.correlationId()));

        return new StartedTurn(thread.getId(), turn.getId(), history);
    }

    @Transactional
    public AssistantTurn succeed(UUID turnId, AssistantAnswer answer) {
        AssistantTurn turn = require(turnId);
        turn.succeed(answer.text(), answer.model(), answer.inputTokens(), answer.outputTokens());
        threads.touch(turn.getThreadId(), Instant.now());
        return turn;
    }

    @Transactional
    public AssistantTurn fail(UUID turnId, ErrorCode code) {
        AssistantTurn turn = require(turnId);
        turn.fail(code.name());
        threads.touch(turn.getThreadId(), Instant.now());
        return turn;
    }

    /**
     * Someone else's thread answers 404, never 403 — telling a colleague that a row exists is what a
     * private tier prevents. {@code StrategySearchService} settles the same question the same way.
     */
    private AssistantThread requireOwnThread(UUID threadId, UUID workspaceId, UUID userId) {
        return threads.findByIdAndWorkspaceIdAndUserId(threadId, workspaceId, userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private AssistantTurn require(UUID turnId) {
        return turns.findById(turnId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private static String titleFrom(String question) {
        String collapsed = question.strip().replaceAll("\\s+", " ");
        return collapsed.length() <= TITLE_LENGTH ? collapsed : collapsed.substring(0, TITLE_LENGTH).strip() + "…";
    }

    /** What the request thread knew and the worker cannot look up: see V65's columns for why. */
    public record TurnOrigin(String ipAddress, String userAgent, String correlationId) {}

    public record StartedTurn(UUID threadId, UUID turnId, List<AssistantExchange> history) {}
}
