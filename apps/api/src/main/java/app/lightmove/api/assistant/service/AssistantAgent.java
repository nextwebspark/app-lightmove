package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.AssistantProposal;
import app.lightmove.api.assistant.model.AssistantTurn;
import app.lightmove.api.assistant.tool.AssistantToolContext;
import app.lightmove.api.assistant.tool.CandidateTools;
import app.lightmove.api.assistant.tool.CompanySearchTools;
import app.lightmove.api.assistant.tool.MandateTools;
import app.lightmove.api.assistant.tool.NamedCompanyTools;
import app.lightmove.api.assistant.tool.ProposalTools;
import app.lightmove.api.assistant.tool.SectorTools;
import app.lightmove.api.assistant.tool.TurnRecorder;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.agent.tools.ToolCallListeners;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Answers an ask as one agent: a short core prompt, the playbooks it loads through the {@code Skill}
 * tool, and every assistant tool beside them. The tools offered are exactly these — nothing the
 * playbook library could run on the host is ever registered.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantAgent {

    private final AssistantModelCall model;
    private final AssistantSkills skills;
    private final MandateTools mandateTools;
    private final CompanySearchTools searchTools;
    private final NamedCompanyTools namedCompanyTools;
    private final SectorTools sectorTools;
    private final ProposalTools proposalTools;
    private final CandidateTools candidateTools;
    private final HiringSideResolver hiringSides;
    private final ObjectMapper json;

    public String answer(String question, List<AssistantTurn> history, AssistantToolContext context) {
        if (context.projectId() == null) {
            throw ApiException.of(ErrorCode.ASSISTANT_UNAVAILABLE);
        }
        rememberEarlierResearch(history, context.recorder());
        Map<String, Object> systemParams = systemParams(context);
        String answer;
        try {
            answer = model.ask(systemParams, toolsFor(context.recorder()), history, question, context);
        } catch (RuntimeException failed) {
            log.warn("Assistant model call failed", failed);
            throw ApiException.of(ErrorCode.ASSISTANT_UNAVAILABLE);
        }
        proposalTools.proposeWhatWasFound(context);
        return answer;
    }

    List<ToolCallback> toolsFor(TurnRecorder recorder) {
        List<ToolCallback> tools = new ArrayList<>();
        tools.add(ToolCallListeners.wrap(skills.tool(), new SkillStepListener(recorder, skills.names(), json)));
        tools.addAll(List.of(ToolCallbacks.from(mandateTools, searchTools, namedCompanyTools, sectorTools,
                proposalTools, candidateTools)));
        return tools;
    }

    private Map<String, Object> systemParams(AssistantToolContext context) {
        return Map.of(
                "hiring", HiringContext.render(hiringSides.resolve(context.workspaceId(), context.projectId())),
                "brief", BriefContext.render(mandateTools.briefOf(context.workspaceId(), context.projectId())));
    }

    /** Researched pages ride on the stored list, so an earlier company can be proposed again unbilled. */
    private static void rememberEarlierResearch(List<AssistantTurn> history, TurnRecorder recorder) {
        for (AssistantTurn turn : history) {
            AssistantProposal suggested = turn.getProposal();
            if (suggested == null) {
                continue;
            }
            suggested.researched().forEach(recorder::remember);
            suggested.companies().stream()
                    .filter(company -> company.operates() != null && company.key() != null)
                    .forEach(company -> recorder.operates(company.key(), company.operates()));
        }
    }
}
