package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.constant.AssistantEventKind;
import app.lightmove.api.assistant.constant.AssistantTurnStatus;
import app.lightmove.api.assistant.dto.AssistantTurnResponse;
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
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class AssistantTurnStore {

    /** Enough of the question to recognise the thread in a list; the full text is on the turn. */
    private static final int TITLE_LENGTH = 80;

    private final AssistantThreadRepository threads;
    private final AssistantTurnRepository turns;
    private final AssistantEventAppender events;

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

        // Checked inside this transaction, so two requests racing cannot both get through. A second
        // question while the first is still running is refused rather than queued: every turn spends
        // real money, and a thread answering two at once reads as interleaved nonsense. A brand new
        // thread cannot have one, so this only ever costs an index hit on a continued conversation.
        if (threadId != null
                && turns.existsByThreadIdAndStatus(thread.getId(), AssistantTurnStatus.RUNNING)) {
            throw ApiException.of(ErrorCode.ASSISTANT_TURN_IN_PROGRESS);
        }

        List<AssistantExchange> history = turns.findByThreadIdOrderByCreatedAtAsc(thread.getId()).stream()
                .map(turn -> new AssistantExchange(turn.getQuestion(), turn.getAnswer()))
                .toList();

        AssistantTurn turn = turns.save(AssistantTurn.running(thread.getId(), userId, workspaceId, question,
                origin.ipAddress(), origin.userAgent(), origin.correlationId()));

        // Same transaction as the row, so the turn, its first event and the NOTIFY announcing it all
        // become visible together. A stream opened on this turn can therefore never see it exist
        // without its opening event.
        events.append(turn.getId(), AssistantEventKind.TURN_STARTED, Map.of("question", question));

        // The response is built here, inside the transaction that wrote the row, rather than by
        // re-reading after the worker is submitted. A re-read races a fast turn, so the 202 body
        // could say SUCCEEDED while the controller javadoc promised RUNNING — and it costs a query.
        return new StartedTurn(thread.getId(), turn.getId(), thread.getProjectId(), history,
                AssistantTurnResponse.of(turn));
    }

    /**
     * Settles the turn and says so on the stream, <b>in one transaction</b>.
     *
     * <p>They must not be split. Announce first and a client that refetches on the event reads a
     * RUNNING turn with no answer; settle first and lose the insert, and the stream never says
     * "done" so the panel reconnects forever. No model call is inside this, so the rule that keeps
     * Vertex out of a transaction is untouched.
     *
     * <p>The answer text is emitted as its own event rather than folded into the terminal one: a
     * client replaying from a cursor should receive the answer as content, and the two payloads then
     * stay small enough that neither is tempted toward the NOTIFY size limit.
     */
    @Transactional
    public AssistantTurn succeed(UUID turnId, AssistantAnswer answer) {
        AssistantTurn turn = require(turnId);
        if (alreadySettled(turn)) {
            return turn;
        }
        turn.succeed(answer.text(), answer.model(), answer.inputTokens(), answer.outputTokens());
        threads.touch(turn.getThreadId(), Instant.now());
        events.append(turnId, AssistantEventKind.ANSWER, Map.of("text", answer.text()));
        events.append(turnId, AssistantEventKind.TURN_FINISHED,
                Map.of("status", turn.getStatus().name()));
        return turn;
    }

    @Transactional
    public AssistantTurn fail(UUID turnId, ErrorCode code) {
        AssistantTurn turn = require(turnId);
        if (alreadySettled(turn)) {
            return turn;
        }
        turn.fail(code.name());
        threads.touch(turn.getThreadId(), Instant.now());
        events.append(turnId, AssistantEventKind.TURN_FINISHED,
                Map.of("status", turn.getStatus().name(), "code", code.name()));
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

    /**
     * Whether someone else got there first.
     *
     * <p>The race is real and one-directional: the sweep can cancel a turn whose worker is merely
     * slow — throttled by Cloud Run, or waiting on a vendor — and that worker then comes back with an
     * answer. Writing it would resurrect a turn every reader has already been told is over, and would
     * append events after the terminal one, which is precisely the reordering {@code seq}'s
     * immutability exists to prevent. The answer is dropped instead, and the log says so.
     */
    private boolean alreadySettled(AssistantTurn turn) {
        if (turn.getStatus() == AssistantTurnStatus.RUNNING) {
            return false;
        }
        log.warn("Assistant turn {} came back after being settled as {}; discarding the result",
                turn.getId(), turn.getStatus());
        return true;
    }

    /**
     * Cancels a stranded turn, but only if it is still running.
     *
     * <p>Conditional because both instances sweep: the loser sees a settled row, changes nothing and
     * announces nothing. {@code CANCELLED} rather than {@code FAILED} because nothing went wrong with
     * the question — the process running it went away.
     *
     * @return whether this call was the one that settled it
     */
    @Transactional
    public boolean cancelIfRunning(UUID turnId) {
        AssistantTurn turn = require(turnId);
        if (turn.getStatus() != AssistantTurnStatus.RUNNING) {
            return false;
        }
        turn.cancel();
        threads.touch(turn.getThreadId(), Instant.now());
        events.append(turnId, AssistantEventKind.TURN_FINISHED,
                Map.of("status", turn.getStatus().name(), "code", "ASSISTANT_TURN_STRANDED"));
        return true;
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

    /**
     * What the accept path needs. {@code accepted} is the turn as it was written — RUNNING, no answer
     * — and is the 202 body; nothing may re-read the row to build it, because by then the worker may
     * have settled it.
     *
     * <p>{@code projectId} is the <b>thread's</b>, which on a continued thread is not necessarily the
     * one the request named: {@code AssistantThread.projectId} is write-once, so a thread keeps the
     * mandate it was started about and a later request naming a different one is ignored. Returning
     * it here is what lets the caller describe that mandate to the model without reading the row
     * again.
     */
    public record StartedTurn(UUID threadId, UUID turnId, UUID projectId,
                              List<AssistantExchange> history,
                              AssistantTurnResponse accepted) {}
}
