package app.lightmove.api.assistant.tool;

import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.position.dto.CompensationDto;
import app.lightmove.api.position.service.PositionService;
import java.util.UUID;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * What the assistant may ask of a mandate's brief.
 *
 * <p>Compensation only, and through {@link PositionService#compensationOf} rather than the brief's
 * own read: that read drafts and saves a brief for a mandate that has none, which a question must
 * never do — least of all one asked under a read-only client seat.
 */
@Component
public class PositionTools implements AssistantToolSubject {

    private final PositionService briefs;

    public PositionTools(PositionService briefs) {
        this.briefs = briefs;
    }

    @Tool(description = """
            Report what a mandate's brief says the role pays: the currency, the base salary range, \
            the bonus and any long-term incentive, and the benefits. Use this to judge whether a \
            company or an executive sits at the mandate's level. A mandate whose brief has not been \
            filled in yet answers with empty figures, which means unstated rather than zero.""")
    @RequiresProjectAction(ProjectAction.WORK_VIEW)
    public CompensationDto mandateCompensation(
            @ToolParam(description = "The mandate's id") String projectId,
            ToolContext toolContext) {
        AssistantToolCaller caller = ToolCallerContext.callerOf(toolContext);
        return briefs.compensationOf(caller.workspaceId(), UUID.fromString(projectId));
    }
}
