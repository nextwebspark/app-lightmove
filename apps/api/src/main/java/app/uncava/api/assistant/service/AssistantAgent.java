package app.uncava.api.assistant.service;

import app.uncava.api.assistant.model.AssistantRoundFailed;
import app.uncava.api.assistant.model.AssistantTurn;
import app.uncava.api.assistant.tool.AssistantToolContext;
import app.uncava.api.assistant.tool.MandateTools;
import app.uncava.api.assistant.tool.ProposalTools;
import app.uncava.api.assistant.tool.TurnRecorder;
import app.uncava.api.core.error.constant.ErrorCode;
import app.uncava.api.core.error.model.ApiException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Answers an ask in one model call over the core prompt, the playbooks and the assistant's tools. A call
 * whose round failed after its tools found companies still answers with them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantAgent {

    /** What a turn that asked says above its questions; the card under it is the answer. */
    static final String ASKED_LEAD_IN = "Before I go on, help me narrow this down:";

    /** What a turn says when the model stopped before writing its answer; the card under it is what was found. */
    static final String STOPPED_EARLY_LEAD_IN =
            "I could not finish this answer. These are the companies I found before it stopped.";

    private final AssistantModelCall model;
    private final AssistantToolset toolset;
    private final MandateTools mandateTools;
    private final ProposalTools proposalTools;
    private final HiringSideResolver hiringSides;

    public String answer(String question, List<AssistantTurn> history, AssistantToolContext context) {
        if (context.projectId() == null) {
            throw ApiException.of(ErrorCode.ASSISTANT_UNAVAILABLE);
        }
        CardMemory.rememberResearchOf(history, context.recorder());
        Map<String, Object> systemParams = systemParams(context);
        String answer;
        try {
            answer = model.ask(systemParams, toolset.forAsk(context.recorder()), history, question, context);
        } catch (AssistantRoundFailed failed) {
            log.warn("Assistant model call failed after {} tool steps", context.recorder().steps().size(), failed);
            return whatWasFoundBeforeItFailed(context);
        } catch (RuntimeException failed) {
            log.warn("Assistant model call failed", failed);
            throw ApiException.of(ErrorCode.ASSISTANT_UNAVAILABLE);
        }
        if (context.recorder().askedQuestions()) {
            return ASKED_LEAD_IN;
        }
        proposalTools.proposeWhatWasFound(context);
        return answer;
    }

    private String whatWasFoundBeforeItFailed(AssistantToolContext context) {
        TurnRecorder recorder = context.recorder();
        if (recorder.askedQuestions()) {
            return ASKED_LEAD_IN;
        }
        try {
            proposalTools.proposeWhatWasFound(context);
        } catch (RuntimeException failed) {
            log.warn("The companies found before the model call failed could not be suggested", failed);
            throw ApiException.of(ErrorCode.ASSISTANT_UNAVAILABLE);
        }
        if (recorder.proposal() == null || recorder.proposal().companies().isEmpty()) {
            throw ApiException.of(ErrorCode.ASSISTANT_UNAVAILABLE);
        }
        return STOPPED_EARLY_LEAD_IN;
    }

    private Map<String, Object> systemParams(AssistantToolContext context) {
        return Map.of(
                "hiring", HiringContext.render(hiringSides.resolve(context.workspaceId(), context.projectId())),
                "brief", BriefContext.render(mandateTools.briefOf(context.workspaceId(), context.projectId())));
    }
}
