package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.tool.AssistantToolContext;
import app.lightmove.api.assistant.tool.CandidateTools;
import app.lightmove.api.assistant.tool.MandateTools;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/** The people: reads who the mandate has mapped, a person's background, and where nobody is mapped yet. */
@Service
public class CandidateSpecialist implements AssistantSpecialist {

    private static final String PROMPT_ID = "assistant-candidates";

    private final CandidateTools candidateTools;
    private final MandateTools mandateTools;
    private final Resource systemPrompt;

    public CandidateSpecialist(CandidateTools candidateTools, MandateTools mandateTools,
                               @Value("classpath:prompts/assistant-candidates.st") Resource systemPrompt) {
        this.candidateTools = candidateTools;
        this.mandateTools = mandateTools;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String toolName() {
        return "askCandidateSpecialist";
    }

    @Override
    public String domain() {
        return "candidates";
    }

    @Override
    public String description() {
        return """
                Knows the executives this position has mapped: who they are, where they work, their \
                status and background, and which companies still have nobody mapped. Read-only. Ask it \
                for anything about people already on this position.""";
    }

    @Override
    public boolean availableTo(AssistantToolContext context) {
        return context.projectId() != null;
    }

    @Override
    public String promptId() {
        return PROMPT_ID;
    }

    @Override
    public Resource systemPrompt() {
        return systemPrompt;
    }

    @Override
    public Map<String, Object> systemParams(AssistantToolContext context) {
        return Map.of("brief", BriefContext.render(mandateTools.briefOf(context.workspaceId(), context.projectId())));
    }

    @Override
    public List<Object> tools() {
        return List.of(candidateTools);
    }
}
