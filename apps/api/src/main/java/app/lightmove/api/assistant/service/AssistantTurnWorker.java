package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.constant.AssistantEventKind;
import app.lightmove.api.assistant.model.AssistantAnswer;
import app.lightmove.api.assistant.model.AssistantExchange;
import app.lightmove.api.assistant.model.AssistantTurn;
import app.lightmove.api.assistant.model.AssistantTurnPrompt;
import app.lightmove.api.assistant.tool.AssistantToolCaller;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.logging.service.CorrelationId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs one turn off the request thread, and is responsible for it ending.
 *
 * <p><b>No {@code @Transactional} anywhere here, deliberately.</b> The model call must not hold a
 * pooled connection: {@code DB_POOL_MAX} is 5, so a handful of turns each pinning one across a
 * multi-second Vertex call would deadlock the instance. Every write goes through
 * {@link AssistantTurnStore} or {@link AssistantEventAppender}, each of which opens its own short
 * transaction — and each of which is a separate bean, because a transactional method called from a
 * sibling of the same class is inert (the {@code AuditEventWriter} trap).
 *
 * <p>Its own bean for the same reason: {@code @Async} on a method called from within the same class
 * would run inline on the request thread, which is exactly the bug this replaces.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantTurnWorker {

    private final AssistantTurnRunner runner;
    private final AssistantTurnStore store;
    private final AssistantEventAppender events;
    private final AuditService audit;

    /**
     * @param correlationId the accepting request's, adopted here so the turn's log lines and its
     *                      audit row tie back to it — a background thread has none of its own
     */
    @Async("assistantTurnExecutor")
    public void run(TurnWork work, String correlationId) {
        CorrelationId.adopt(correlationId);
        try {
            settle(work);
        } finally {
            CorrelationId.release();
        }
    }

    /**
     * The model call, with both endings written.
     *
     * <p>A broad catch because anything escaping would leave the turn RUNNING for ever: the SPA would
     * reconnect every 55s to a stream that will never speak, and V65's partial index on running turns
     * would fill with rows the sweep cannot tell from live ones.
     */
    private void settle(TurnWork work) {
        AssistantTurn settled;
        try {
            AssistantAnswer answer = runner.run(
                    new AssistantTurnPrompt(work.systemPrompt(), work.history(), work.question()),
                    // Identity rebuilt from the turn row, which is why V65 stores it: this thread has
                    // no SecurityContext, and the guard beans still re-read the database per call.
                    new AssistantToolCaller(work.actorUserId(), work.workspaceId(), work.turnId()),
                    sinkFor(work.turnId()));
            settled = store.succeed(work.turnId(), answer);
        } catch (ApiException failed) {
            log.warn("Assistant turn {} failed: {}", work.turnId(), failed.getCode());
            settled = failOrGiveUp(work.turnId(), failed.getCode());
        } catch (RuntimeException failed) {
            log.error("Assistant turn {} failed", work.turnId(), failed);
            settled = failOrGiveUp(work.turnId(), ErrorCode.INTERNAL_ERROR);
        }
        if (settled != null) {
            recordSpend(work, settled);
        }
    }

    /**
     * Every kind a running turn emits, each one row through the appender's own short transaction.
     *
     * <p><b>Synchronised, and not defensively.</b> The appender allocates {@code max(seq) + 1} and
     * leans on a turn having one writer, which used to be this thread alone. It is not any more:
     * answer text is drained here while a tool's own events are emitted from inside
     * {@code ToolCallingAdvisor}'s chain, on whichever thread that is. Two appends reading the same
     * max would fail loudly on V65's {@code app_lm_assistant_event_seq_uk} — correct, and still a
     * turn lost to a collision nothing forced. One sink per turn, so this serialises that turn and
     * contends with nothing else.
     */
    private AssistantEventSink sinkFor(UUID turnId) {
        return new AssistantEventSink() {

            @Override
            public synchronized void delta(String text) {
                events.append(turnId, AssistantEventKind.MESSAGE_DELTA, Map.of("text", text));
            }

            @Override
            public synchronized void toolCalled(String toolName, String arguments) {
                events.append(turnId, AssistantEventKind.TOOL_CALLED,
                        Map.of("tool", toolName, "arguments", arguments));
            }

            @Override
            public synchronized void toolResult(String toolName, String result) {
                events.append(turnId, AssistantEventKind.TOOL_RESULT,
                        Map.of("tool", toolName, "result", result));
            }

            @Override
            public synchronized void proposal(Map<String, Object> payload) {
                events.append(turnId, AssistantEventKind.PROPOSAL, payload);
            }
        };
    }

    /**
     * Settling can itself fail — a seq collision rolls the terminal transaction back. One more
     * attempt to mark the turn FAILED, then the sweep is the backstop; what must not happen is an
     * exception escaping into the executor, where nothing would connect it to a turn.
     */
    private AssistantTurn failOrGiveUp(UUID turnId, ErrorCode code) {
        try {
            return store.fail(turnId, code);
        } catch (RuntimeException unsettlable) {
            log.error("Could not settle assistant turn {}; leaving it to the sweep", turnId,
                    unsettlable);
            return null;
        }
    }

    /**
     * Recorded for the reason a contact lookup is: it spends money against the firm's account on a
     * named person's behalf, and the cost has to be attributable after the fact. The origin comes
     * from the turn row rather than a request, because Tomcat recycled the request at the 202.
     */
    private void recordSpend(TurnWork work, AssistantTurn settled) {
        audit.event(WorkspaceEventType.ASSISTANT_TURN_RAN)
                .actor(work.actorUserId())
                .workspace(work.workspaceId())
                .target("assistantThread", work.threadId())
                .origin(work.ipAddress(), work.userAgent())
                .detail("turnId", work.turnId())
                .detail("status", settled.getStatus().name())
                .detailIfPresent("model", settled.getModel())
                .detailIfPresent("inputTokens", settled.getInputTokens())
                .detailIfPresent("outputTokens", settled.getOutputTokens())
                .record();
    }

    /**
     * Everything the worker needs, resolved on the request thread.
     *
     * <p>History and question are passed rather than re-read: {@code begin} reads history
     * <i>before</i> saving the new turn precisely so the in-flight question is not sent to the model
     * twice, and re-reading here would put that bug straight back.
     */
    public record TurnWork(UUID turnId, UUID threadId, UUID actorUserId, UUID workspaceId,
                           String systemPrompt, List<AssistantExchange> history, String question,
                           String ipAddress, String userAgent) {}
}
