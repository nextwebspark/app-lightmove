package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.dto.AssistantThreadResponse;
import app.lightmove.api.assistant.dto.AssistantThreadSummary;
import app.lightmove.api.assistant.dto.AssistantTurnResponse;
import app.lightmove.api.assistant.model.AssistantProposal;
import app.lightmove.api.assistant.model.AssistantStepEvent;
import app.lightmove.api.assistant.model.AssistantThread;
import app.lightmove.api.assistant.model.AssistantTurn;
import app.lightmove.api.assistant.repository.AssistantThreadRepository;
import app.lightmove.api.assistant.repository.AssistantTurnRepository;
import app.lightmove.api.assistant.tool.AssistantToolContext;
import app.lightmove.api.assistant.tool.TurnRecorder;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.AssistantSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
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
 * the chat. Nothing is written when the model call fails.
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
    private final AssistantSettings settings;

    public AssistantService(AssistantThreadRepository threads, AssistantTurnRepository turns,
                            AssistantAgent agent, TransactionTemplate transactions, AuditService audit,
                            LightMoveProperties properties) {
        this.threads = threads;
        this.turns = turns;
        this.agent = agent;
        this.transactions = transactions;
        this.audit = audit;
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
                    recorder.steps(), recorder.proposal()));
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
                .detail("skills", String.join(",", recorder.skillsUsed()))
                .detail("companiesOnCard", String.valueOf(
                        recorder.proposal() == null ? 0 : recorder.proposal().companies().size()))
                .record();
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
