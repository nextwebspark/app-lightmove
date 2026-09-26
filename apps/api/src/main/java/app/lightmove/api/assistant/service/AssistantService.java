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
import app.lightmove.api.assistant.tool.CompanySearchTools;
import app.lightmove.api.assistant.tool.MandateTools;
import app.lightmove.api.assistant.tool.NamedCompanyTools;
import app.lightmove.api.assistant.tool.ProposalTools;
import app.lightmove.api.assistant.tool.SectorTools;
import app.lightmove.api.assistant.tool.TurnRecorder;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.AssistantSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.workspace.service.FirmService;
import app.lightmove.api.core.llm.service.ChatCallLog;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Answers a question in one request: the model searches the universe, proposes a card, and the
 * answer is saved as a turn of the chat. Nothing is written when the model call fails.
 */
@Slf4j
@Service
public class AssistantService {

    static final String PROMPT_ID = "assistant-turn";

    private static final int MAX_THREADS = 50;
    private static final int MAX_TITLE = 80;

    private final AssistantThreadRepository threads;
    private final AssistantTurnRepository turns;
    private final ChatClient chatClient;
    private final CompanySearchTools searchTools;
    private final ProposalTools proposalTools;
    private final MandateTools mandateTools;
    private final SectorTools sectorTools;
    private final NamedCompanyTools namedCompanyTools;
    private final TransactionTemplate transactions;
    private final AuditService audit;
    private final FirmService firms;
    private final Resource systemPrompt;
    private final AssistantSettings settings;

    public AssistantService(AssistantThreadRepository threads, AssistantTurnRepository turns,
                            ChatClient chatClient, CompanySearchTools searchTools,
                            ProposalTools proposalTools, MandateTools mandateTools, SectorTools sectorTools,
                            NamedCompanyTools namedCompanyTools,
                            TransactionTemplate transactions, AuditService audit, FirmService firms,
                            @Value("classpath:prompts/assistant-system.st") Resource systemPrompt,
                            LightMoveProperties properties) {
        this.threads = threads;
        this.turns = turns;
        this.chatClient = chatClient;
        this.searchTools = searchTools;
        this.proposalTools = proposalTools;
        this.mandateTools = mandateTools;
        this.sectorTools = sectorTools;
        this.namedCompanyTools = namedCompanyTools;
        this.transactions = transactions;
        this.audit = audit;
        this.firms = firms;
        this.systemPrompt = systemPrompt;
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
        List<AssistantTurn> history = existing == null ? List.of()
                : turns.findByThreadIdOrderByCreatedAtAsc(existing.getId());

        TurnRecorder recorder = new TurnRecorder(onStep, onProposal);
        AssistantToolContext context = new AssistantToolContext(workspaceId, projectId, recorder);
        String answer = callModel(question, history, context);
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
                .actor(userId).workspace(workspaceId).target(AuditService.PROJECT_TARGET, projectId)
                .detail("threadId", saved.threadId().toString())
                .detail("turnId", saved.id().toString())
                .detail("vendorSearches", String.valueOf(recorder.vendorSearches()))
                .detail("companiesOnCard", String.valueOf(
                        recorder.proposal() == null ? 0 : recorder.proposal().companies().size()))
                .record();
        return saved;
    }

    /**
     * Deliberately not {@code LlmCallPolicy.forPrompt}: its SafeGuardAdvisor phrase list would refuse
     * ordinary conversation ("ignore the declined ones"); #429 owns the replacement. The ChatCallLog
     * attribution is kept, since that keeps prompt and answer content out of the logs.
     */
    private String callModel(String question, List<AssistantTurn> history, AssistantToolContext context) {
        try {
            String answer = chatClient.prompt()
                    .advisors(advisors -> advisors.param(ChatCallLog.PROMPT_ID_ATTRIBUTE, PROMPT_ID))
                    .options(GoogleGenAiChatOptions.builder()
                            .model(settings.model())
                            .temperature(settings.temperature())
                            .thinkingBudget(settings.thinkingBudget())
                            .labels(Map.of("prompt", PROMPT_ID)))
                    .system(system -> system.text(systemPrompt)
                            .param("firm", FirmContext.render(firms.firmOf(context.workspaceId()))))
                    .messages(conversation(history, question))
                    .tools(mandateTools, searchTools, namedCompanyTools, sectorTools, proposalTools)
                    .toolContext(context.asMap())
                    .call()
                    .content();
            return answer == null ? "" : answer.strip();
        } catch (RuntimeException failed) {
            log.warn("Assistant model call failed", failed);
            throw ApiException.of(ErrorCode.ASSISTANT_UNAVAILABLE);
        }
    }

    private List<Message> conversation(List<AssistantTurn> history, String question) {
        List<AssistantTurn> recent = history.subList(
                Math.max(0, history.size() - settings.historyWindow()), history.size());
        List<Message> messages = new ArrayList<>(recent.size() * 2 + 1);
        for (AssistantTurn turn : recent) {
            messages.add(new UserMessage(turn.getQuestion()));
            messages.add(new AssistantMessage(turn.getAnswer()));
        }
        messages.add(new UserMessage(question));
        return messages;
    }

    private static String titleOf(String question) {
        String flattened = question.replaceAll("\\s+", " ").strip();
        return flattened.length() <= MAX_TITLE ? flattened : flattened.substring(0, MAX_TITLE).strip();
    }
}
