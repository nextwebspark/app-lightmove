package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.dto.AssistantThreadResponse;
import app.lightmove.api.assistant.dto.AssistantThreadSummary;
import app.lightmove.api.assistant.dto.AssistantTurnResponse;
import app.lightmove.api.assistant.model.AssistantProposal;
import app.lightmove.api.assistant.model.AssistantStepEvent;
import app.lightmove.api.assistant.model.AssistantThread;
import app.lightmove.api.assistant.model.AssistantTurn;
import app.lightmove.api.assistant.model.MandateBrief;
import app.lightmove.api.assistant.repository.AssistantThreadRepository;
import app.lightmove.api.assistant.repository.AssistantTurnRepository;
import app.lightmove.api.assistant.tool.AssistantToolContext;
import app.lightmove.api.assistant.tool.ProposalTools;
import app.lightmove.api.assistant.tool.TurnRecorder;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.position.service.PositionService;
import app.lightmove.api.workspace.service.FirmService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Answers a question in one request: the model searches the universe, proposes a card, and the
 * answer is saved as a turn of the chat. Nothing is written when the model call fails.
 */
@Service
@RequiredArgsConstructor
public class AssistantService {

    private static final int MAX_THREADS = 50;
    private static final int MAX_TITLE = 80;

    private final AssistantThreadRepository threads;
    private final AssistantTurnRepository turns;
    private final AssistantModelCall model;
    private final ProposalTools proposalTools;
    private final TransactionTemplate transactions;
    private final AuditService audit;
    private final FirmService firms;
    private final PositionService positions;

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
     * The chat a question continues, or null for a new one. Called before the answer starts
     * streaming, so someone else's chat, or one from another project, is a plain 404.
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
                                     Consumer<AssistantProposal> onProposal,
                                     Consumer<String> onAnswer) {
        List<AssistantTurn> history = existing == null ? List.of()
                : turns.findByThreadIdOrderByCreatedAtAsc(existing.getId());

        TurnRecorder recorder = new TurnRecorder(onStep, onProposal);
        AssistantToolContext context = new AssistantToolContext(workspaceId, projectId, recorder);
        String answer = model.answer(question, history, promptFacts(workspaceId, projectId), context, onAnswer);
        proposalTools.proposeWhatWasFound(context);

        AssistantTurnResponse saved = transactions.execute(status -> {
            AssistantThread thread = existing != null ? existing
                    : threads.save(AssistantThread.of(workspaceId, userId, projectId,
                            titleOf(question)));
            AssistantTurn turn = turns.saveAndFlush(AssistantTurn.answered(thread, question, answer,
                    recorder.steps(), recorder.proposal()));
            threads.touch(thread.getId(), Instant.now());
            return AssistantTurnResponse.of(turn);
        });
        audit.event(ProjectEventType.ASSISTANT_ASKED)
                .actor(userId).workspace(workspaceId).target("project", projectId)
                .detail("threadId", saved.threadId().toString())
                .detail("turnId", saved.id().toString())
                .detail("vendorSearches", String.valueOf(recorder.vendorSearches()))
                .detail("companiesOnCard", String.valueOf(
                        recorder.proposal() == null ? 0 : recorder.proposal().companies().size()))
                .record();
        return saved;
    }

    private AssistantPromptFacts promptFacts(UUID workspaceId, UUID projectId) {
        return new AssistantPromptFacts(FirmContext.render(firms.firmOf(workspaceId)),
                PositionContext.render(MandateBrief.of(positions.briefOf(workspaceId, projectId))));
    }

    private static String titleOf(String question) {
        String flattened = question.replaceAll("\\s+", " ").strip();
        return flattened.length() <= MAX_TITLE ? flattened : flattened.substring(0, MAX_TITLE).strip();
    }
}
