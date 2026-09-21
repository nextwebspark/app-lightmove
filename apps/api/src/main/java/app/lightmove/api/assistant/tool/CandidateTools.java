package app.lightmove.api.assistant.tool;

import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.security.rbac.ProjectAction;
import java.util.List;
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
            returned. Only the first executives are listed when a mandate has mapped more than fit \
            in one answer.""")
    @RequiresProjectAction(ProjectAction.WORK_VIEW)
    public List<MandateExecutiveSummary> listMandateExecutives(
            @ToolParam(description = "The mandate's id") String projectId,
            ToolContext toolContext) {
        AssistantToolCaller caller = ToolCallerContext.callerOf(toolContext);
        return candidates.listAllOfProject(caller.workspaceId(), UUID.fromString(projectId), maxRows)
                .candidates().stream()
                .map(MandateExecutiveSummary::of)
                .toList();
    }
}
