package app.lightmove.api.assistant.tool;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * What the assistant may ask of the company universe.
 *
 * <p>Lives here rather than in {@code strategy} because a tool description is prompt text — it is
 * tuned against how the model behaves, not against the domain — and because the set of tools the
 * assistant holds is the assistant's decision. It calls {@code strategy}'s public service and adds
 * nothing: a tool that grew rules of its own would be a second implementation of the market.
 *
 * <p>Guarded at the workspace tier because the universe belongs to no mandate. {@code PROJECT_BROWSE}
 * is ADMIN and MEMBER only, so a pure client representative is refused here while the mandate-side
 * tools, which their seat does grant, still answer.
 */
@Component
public class CompanySearchTools implements AssistantToolSubject {

    private final ApolloCompanyQueryService companies;
    private final int maxRows;

    public CompanySearchTools(ApolloCompanyQueryService companies, LightMoveProperties properties) {
        this.companies = companies;
        this.maxRows = properties.assistant().toolRowLimit();
    }

    @Tool(description = """
            Search the company universe by name and return the companies that match. \
            Use this to check whether a company is in the market data before suggesting it. \
            Returns an Apollo account id per company, which is what identifies it everywhere else.""")
    @RequiresWorkspaceAction(WorkspaceAction.PROJECT_BROWSE)
    public List<MarketCompanySummary> searchCompanyUniverse(
            @ToolParam(description = "All or part of a company name") String companyName) {
        return companies.typeahead(companyName, maxRows).stream()
                .map(MarketCompanySummary::of)
                .toList();
    }
}
