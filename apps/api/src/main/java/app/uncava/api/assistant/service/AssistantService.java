package app.uncava.api.assistant.service;

import app.uncava.api.assistant.dto.AssistantThreadResponse;
import app.uncava.api.assistant.dto.AssistantThreadSummary;
import app.uncava.api.assistant.dto.AssistantTurnResponse;
import app.uncava.api.assistant.model.AssistantProposal;
import app.uncava.api.assistant.model.AssistantStepEvent;
import app.uncava.api.assistant.model.AssistantThread;
import app.uncava.api.assistant.model.AssistantTurn;
import app.uncava.api.assistant.repository.AssistantThreadRepository;
import app.uncava.api.assistant.repository.AssistantTurnRepository;
import app.uncava.api.assistant.tool.AssistantToolContext;
import app.uncava.api.assistant.tool.TurnRecorder;
import app.uncava.api.billing.usage.constant.UsageKind;
import app.uncava.api.billing.usage.model.MeteredUse;
import app.uncava.api.billing.usage.service.UsageRecorder;
import app.uncava.api.core.audit.constant.ProjectEventType;
import app.uncava.api.core.audit.service.AuditService;
import app.uncava.api.core.config.AssistantSettings;
import app.uncava.api.core.config.UncavaProperties;
import app.uncava.api.core.error.constant.ErrorCode;
import app.uncava.api.core.error.model.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Answers a question in one request through {@link AssistantAgent}, and saves the answer as a turn of
 * the chat. Nothing is written when the model call fails before finding anything.
 */
@Slf4j
@Service
public class AssistantService {

    private static final int MAX_THREADS = 50;
    private static final int MAX_TITLE = 80;

    private final AssistantThreadRepository threads;
    private final AssistantTurnRepository turns;
    private final AssistantAgent agent;
    private final TransactionTemplate transactions;
    private final AuditService audit;
    private final UsageRecorder usage;
    private final AssistantSettings settings;

    public AssistantService(AssistantThreadRepository threads, AssistantTurnRepository turns,
                            AssistantAgent agent, TransactionTemplate transactions, AuditService audit,
                            UsageRecorder usage, UncavaProperties properties) {
        this.threads = threads;
        this.turns = turns;
        this.agent = agent;
        this.transactions = transactions;
        this.audit = audit;
        this.usage = usage;
        this.settings = properties.assistant();
    }

    public List<AssistantThreadSummary> threads(UUID userId, UUID workspaceId, UUID projectId) {
        return threads.findByWorkspaceIdAndUserIdAndProjectIdOrderByUpdatedAtDesc(
                        workspaceId, userId, projectId, PageRequest.of(0, MAX_THREADS))
                .stream().map(AssistantThreadSummary::of).toList();
    }

    public AssistantThreadResponse thread(UUID threadId, UUID userId, UUID workspaceId) {
        AssistantThread thread = threads.findByIdAndWorkspaceIdAndUserId(threadId, workspaceId, userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        List<AssistantTurnResponse> answered = turns.findByThreadIdOrderByCreatedAtAsc(thread.getId())
                .stream().map(AssistantTurnResponse::of).toList();
        return new AssistantThreadResponse(thread.getId(), thread.getTitle(), thread.getProjectId(),
                answered);
    }

    /**
     * Null for a new chat. Called before the answer starts streaming, so someone else's chat, or one
     * from another project, is a plain 404.
     */
    public AssistantThread requireThread(UUID userId, UUID workspaceId, UUID projectId, UUID threadId) {
        if (threadId == null) {
            return null;
        }
        return threads.findByIdAndWorkspaceIdAndUserId(threadId, workspaceId, userId)
                .filter(thread -> projectId.equals(thread.getProjectId()))
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /**
     * The chat a first question starts, saved before it is answered so the stream can name it and the
     * panel can look an answer that outlives its stream up by id.
     */
    public AssistantThread foundThread(UUID userId, UUID workspaceId, UUID projectId, String question) {
        return threads.save(AssistantThread.of(workspaceId, userId, projectId, titleOf(question)));
    }

    /** Takes back a chat whose first question was never answered. */
    public void discardIfUnanswered(AssistantThread founded) {
        transactions.executeWithoutResult(status -> {
            if (turns.findByThreadIdOrderByCreatedAtAsc(founded.getId()).isEmpty()) {
                threads.deleteById(founded.getId());
            }
        });
    }

    /** The project was authorised by the controller and {@code existing} by {@link #requireThread}. */
    public AssistantTurnResponse ask(UUID userId, UUID workspaceId, UUID projectId,
                                     AssistantThread existing, String question,
                                     Consumer<AssistantStepEvent> onStep,
                                     Consumer<AssistantProposal> onProposal) {
        long startedAt = System.nanoTime();
        List<AssistantTurn> history = recentOf(existing == null ? List.of()
                : turns.findByThreadIdOrderByCreatedAtAsc(existing.getId()));

        TurnRecorder recorder = new TurnRecorder(onStep, onProposal);
        AssistantToolContext context = new AssistantToolContext(workspaceId, projectId, recorder);
        String answer = agent.answer(question, history, context);

        AssistantTurnResponse saved = transactions.execute(status -> {
            AssistantThread thread = existing != null ? existing
                    : threads.save(AssistantThread.of(workspaceId, userId, projectId,
                            titleOf(question)));
            AssistantTurn turn = turns.saveAndFlush(AssistantTurn.answered(thread, question, answer,
                    recorder.steps(), recorder.askedQuestions() ? null : recorder.proposal(),
                    recorder.questions(), recorder.spend()));
            threads.touch(thread.getId(), Instant.now());
            return AssistantTurnResponse.of(turn);
        });
        long answerMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("Assistant answered in {}ms with {} tool steps", answerMs, recorder.steps().size());
        audit.event(ProjectEventType.ASSISTANT_ASKED)
                .actor(userId).workspace(workspaceId).target(AuditService.PROJECT_TARGET, projectId)
                .detail("threadId", saved.threadId().toString())
                .detail("turnId", saved.id().toString())
                .detail("vendorSearches", String.valueOf(recorder.vendorSearches()))
                .detail("answerMs", String.valueOf(answerMs))
                .detail("toolSteps", String.valueOf(recorder.steps().size()))
                .detail("rounds", String.valueOf(recorder.roundTimings().rounds()))
                .detail("modelMs", String.valueOf(recorder.roundTimings().modelMs()))
                .detail("slowestRoundMs", String.valueOf(recorder.roundTimings().slowestRoundMs()))
                .detail("roundsReAsked", String.valueOf(recorder.roundTimings().reAsked()))
                .detail("toolStepMsTotal", String.valueOf(recorder.toolStepMsTotal()))
                .detail("skills", String.join(",", recorder.skillsUsed()))
                .detail("questionsAsked", String.valueOf(recorder.questions().size()))
                .detail("companiesOnCard", String.valueOf(
                        recorder.proposal() == null ? 0 : recorder.proposal().companies().size()))
                .record();
        usage.record(MeteredUse.of(workspaceId, userId, projectId, UsageKind.ASSISTANT_ASK, 1));
        return saved;
    }

    private List<AssistantTurn> recentOf(List<AssistantTurn> history) {
        return history.subList(Math.max(0, history.size() - settings.historyWindow()), history.size());
    }

    private static String titleOf(String question) {
        String flattened = question.replaceAll("\\s+", " ").strip();
        return flattened.length() <= MAX_TITLE ? flattened : flattened.substring(0, MAX_TITLE).strip();
    }
}
