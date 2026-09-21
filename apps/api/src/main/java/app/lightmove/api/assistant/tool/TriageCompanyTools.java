package app.lightmove.api.assistant.tool;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.model.TriageCompanyFilters;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import java.util.UUID;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * What the assistant may ask of one mandate's own companies.
 *
 * <p>The mandate is an argument rather than something read from the thread, which is what lets
 * {@link AuthorisingToolCallback} check it: the thread's own {@code project_id} is context the model
 * was given, and authorising against it would authorise the model's question with the model's own
 * premise.
 */
@Component
public class TriageCompanyTools implements AssistantToolSubject {

    private final TriageCompanyService companies;
    private final int maxRows;

    public TriageCompanyTools(TriageCompanyService companies, LightMoveProperties properties) {
        this.companies = companies;
        this.maxRows = properties.assistant().toolRowLimit();
    }

    /**
     * The {@code ToolContext} parameter is Spring AI's own seam for state the model must not supply.
     * It is injected per call and left out of the schema the model is shown, so the workspace this
     * scopes on is the one the guard already authorised the mandate against — never a value the
     * model could name.
     */
    @Tool(description = """
            List the companies a mandate has filed at one stage. \
            Stages are inUniverse (taken into the mandate's universe), shortlisted, and declined. \
            Use this to answer questions about what a mandate has already covered. The answer says \
            how many the stage holds in total and how many are shown — when those differ you are \
            seeing the first of them, not all, so quote the total rather than counting the list.""")
    @RequiresProjectAction(ProjectAction.WORK_VIEW)
    public MandateCompanies listMandateCompanies(
            @ToolParam(description = "The mandate's id") String projectId,
            @ToolParam(description = "One of inUniverse, shortlisted, declined") String stage,
            ToolContext toolContext) {
        TriageCompanyStatus status = TriageCompanyStatus.fromValue(stage);
        if (status == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown triage stage: " + stage);
        }
        AssistantToolCaller caller = ToolCallerContext.callerOf(toolContext);
        TriageCompaniesResponse filed = companies.listAllOfStage(caller.workspaceId(),
                UUID.fromString(projectId), status, TriageCompanyFilters.none(), maxRows);
        return MandateCompanies.of(filed.totalCount(), filed.companies());
    }
}
