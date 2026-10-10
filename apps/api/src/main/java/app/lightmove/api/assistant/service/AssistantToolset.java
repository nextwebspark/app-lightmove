package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.tool.CandidateTools;
import app.lightmove.api.assistant.tool.CompanyDetailTools;
import app.lightmove.api.assistant.tool.CompanyDiscoveryTools;
import app.lightmove.api.assistant.tool.CompanySearchTools;
import app.lightmove.api.assistant.tool.MandateTools;
import app.lightmove.api.assistant.tool.NamedCompanyTools;
import app.lightmove.api.assistant.tool.ProposalTools;
import app.lightmove.api.assistant.tool.SectorTools;
import app.lightmove.api.assistant.tool.TurnRecorder;
import java.util.ArrayList;
import java.util.List;
import org.springaicommunity.agent.tools.ToolCallListeners;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Every tool the assistant's model is offered, and nothing else: the playbooks' {@code Skill} tool, the
 * library's question tool ({@link AskUserQuestionCallback}) and the assistant's own {@code @Tool}s. The
 * library's shell, file and web tools are never among them.
 */
@Component
public class AssistantToolset {

    private final AssistantSkills skills;
    private final ObjectMapper json;
    private final List<ToolCallback> assistantTools;

    public AssistantToolset(AssistantSkills skills, ObjectMapper json, MandateTools mandateTools,
                            CompanySearchTools searchTools, NamedCompanyTools namedCompanyTools,
                            CompanyDiscoveryTools discoveryTools, SectorTools sectorTools,
                            ProposalTools proposalTools, CandidateTools candidateTools,
                            CompanyDetailTools detailTools) {
        this.skills = skills;
        this.json = json;
        this.assistantTools = List.of(ToolCallbacks.from(mandateTools, searchTools, namedCompanyTools,
                discoveryTools, sectorTools, proposalTools, candidateTools, detailTools));
    }

    /** The {@code Skill} tool and the question tool are per ask: both write to that ask's recorder. */
    public List<ToolCallback> forAsk(TurnRecorder recorder) {
        List<ToolCallback> tools = new ArrayList<>(assistantTools.size() + 2);
        tools.add(ToolCallListeners.wrap(skills.tool(), new SkillStepListener(recorder, skills.names(), json)));
        tools.add(AskUserQuestionCallback.forAsk(recorder, json));
        tools.addAll(assistantTools);
        return tools;
    }
}
