package app.lightmove.api.assistant.tool;

import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.security.rbac.ProjectAction;
import java.util.UUID;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * What the assistant may ask of the people a mandate has mapped.
 *
 * <p>Read at {@code WORK_VIEW}, the gate that already reads the Companies grid, so a client
 * representative may ask about their own mandate's map — and see exactly what that grid shows them.
 */
@Component
public class CandidateTools implements AssistantToolSubject {

    private final CandidateService candidates;
    private final int maxRows;

    public CandidateTools(CandidateService candidates, LightMoveProperties properties) {
        this.candidates = candidates;
        this.maxRows = properties.assistant().toolRowLimit();
    }

    @Tool(description = """
            List the executives a mandate has mapped, with the company each was mapped at and where \
            they stand. Use this to answer who has already been covered. Contact details are not \
            returned. The answer says how many the mandate has mapped in total and how many are \
            shown — when those differ you are seeing the first of them, not all, so quote the total \
            rather than counting the list.""")
    @RequiresProjectAction(ProjectAction.WORK_VIEW)
    public MandateExecutives listMandateExecutives(
            @ToolParam(description = "The mandate's id") String projectId,
            ToolContext toolContext) {
        AssistantToolCaller caller = ToolCallerContext.callerOf(toolContext);
        CandidatesResponse mapped = candidates.listAllOfProject(caller.workspaceId(),
                UUID.fromString(projectId), maxRows);
        return MandateExecutives.of(mapped.totalCount(), mapped.candidates());
    }
}
