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
    private final HiringSideResolver hiringSides;
    private final Resource systemPrompt;
    private final AssistantSettings settings;

    public AssistantService(AssistantThreadRepository threads, AssistantTurnRepository turns,
                            ChatClient chatClient, CompanySearchTools searchTools,
                            ProposalTools proposalTools, MandateTools mandateTools, SectorTools sectorTools,
                            NamedCompanyTools namedCompanyTools,
                            TransactionTemplate transactions, AuditService audit, HiringSideResolver hiringSides,
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
        this.hiringSides = hiringSides;
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
        long startedAt = System.nanoTime();
        List<AssistantTurn> history = recentOf(existing == null ? List.of()
                : turns.findByThreadIdOrderByCreatedAtAsc(existing.getId()));

        TurnRecorder recorder = new TurnRecorder(onStep, onProposal);
        rememberEarlierCards(history, recorder);
        AssistantToolContext context = new AssistantToolContext(workspaceId, projectId, recorder);
        String answer = CardMemory.stripFrom(callModel(question, history, context));
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
        long answerMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("Assistant answered in {}ms with {} tool steps", answerMs, recorder.steps().size());
        audit.event(ProjectEventType.ASSISTANT_ASKED)
                .actor(userId).workspace(workspaceId).target(AuditService.PROJECT_TARGET, projectId)
                .detail("threadId", saved.threadId().toString())
                .detail("turnId", saved.id().toString())
                .detail("vendorSearches", String.valueOf(recorder.vendorSearches()))
                .detail("answerMs", String.valueOf(answerMs))
                .detail("toolSteps", String.valueOf(recorder.steps().size()))
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
                            .param("hiring", HiringContext.render(
                                    hiringSides.resolve(context.workspaceId(), context.projectId()))))
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

    private List<AssistantTurn> recentOf(List<AssistantTurn> history) {
        return history.subList(Math.max(0, history.size() - settings.historyWindow()), history.size());
    }

    /** Researched pages ride on the stored card, so an earlier company can be proposed again unbilled. */
    private static void rememberEarlierCards(List<AssistantTurn> history, TurnRecorder recorder) {
        for (AssistantTurn turn : history) {
            AssistantProposal card = turn.getProposal();
            if (card == null) {
                continue;
            }
            card.researched().forEach(recorder::remember);
            card.companies().stream()
                    .filter(company -> company.operates() != null && company.key() != null)
                    .forEach(company -> recorder.operates(company.key(), company.operates()));
        }
    }

    /**
     * Each earlier answer carries the card it showed, which the answer's own text never lists — the
     * newest {@link CardMemory#CARDS_LISTED_IN_FULL} row by row, older ones as a title and a count.
     */
    private static List<Message> conversation(List<AssistantTurn> history, String question) {
        List<Message> messages = new ArrayList<>(history.size() * 2 + 1);
        int cardsLeft = (int) history.stream().filter(AssistantService::hasCard).count();
        for (AssistantTurn turn : history) {
            messages.add(new UserMessage(turn.getQuestion()));
            if (!hasCard(turn)) {
                messages.add(new AssistantMessage(turn.getAnswer()));
                continue;
            }
            boolean listed = cardsLeft-- <= CardMemory.CARDS_LISTED_IN_FULL;
            messages.add(new AssistantMessage(turn.getAnswer() + "\n\n"
                    + CardMemory.render(turn.getProposal(), turn.getProposalAccepted(), listed)));
        }
        messages.add(new UserMessage(question));
        return messages;
    }

    private static boolean hasCard(AssistantTurn turn) {
        return turn.getProposal() != null && !turn.getProposal().companies().isEmpty();
    }

    private static String titleOf(String question) {
        String flattened = question.replaceAll("\\s+", " ").strip();
        return flattened.length() <= MAX_TITLE ? flattened : flattened.substring(0, MAX_TITLE).strip();
    }
}
