package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.AssistantTurn;
import app.lightmove.api.assistant.tool.AssistantToolContext;
import app.lightmove.api.assistant.tool.MandateTools;
import app.lightmove.api.assistant.tool.ProposalTools;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Answers an ask in one model call over the core prompt, the playbooks and the assistant's tools. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantAgent {

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
        } catch (RuntimeException failed) {
            log.warn("Assistant model call failed", failed);
            throw ApiException.of(ErrorCode.ASSISTANT_UNAVAILABLE);
        }
        proposalTools.proposeWhatWasFound(context);
        return answer;
    }

    private Map<String, Object> systemParams(AssistantToolContext context) {
        return Map.of(
                "hiring", HiringContext.render(hiringSides.resolve(context.workspaceId(), context.projectId())),
                "brief", BriefContext.render(mandateTools.briefOf(context.workspaceId(), context.projectId())));
    }
}
