package app.lightmove.api.assistant.tool;

import app.lightmove.api.core.security.rbac.WorkspaceAction;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * What the assistant may ask of the company universe.
 *
 * <p>Lives here rather than in {@code strategy} because a tool description is prompt text — it is
 * tuned against how the model behaves, not against the domain — and because the set of tools the
 * assistant holds is the assistant's decision. It calls {@link MarketSearch} and adds nothing: a
 * tool that grew rules of its own would be a second implementation of the market.
 *
 * <p>Guarded at the workspace tier because the universe belongs to no mandate. {@code PROJECT_BROWSE}
 * is ADMIN and MEMBER only, so a pure client representative is refused here while the mandate-side
 * tools, which their seat does grant, still answer.
 *
 * <p>No off-limits list applies here, and that is not an omission: off-limits belongs to a mandate's
 * saved filter and this tier has no mandate. {@link StrategyTools} is the search that reads one
 * mandate's own view of the same market.
 */
@Component
public class CompanySearchTools implements AssistantToolSubject {

    private final MarketSearch market;

    public CompanySearchTools(MarketSearch market) {
        this.market = market;
    }

    @Tool(description = """
            Search the company universe and return the largest matching companies, biggest first. \
            Every argument is optional and an omitted one places no constraint, so give only what \
            the question asks for. Country and industry must be spelled exactly as describeMarket \
            reports them. The answer says how many companies matched in total and how many are \
            shown — when those differ you are seeing the largest, not all of them, so narrow the \
            search rather than reporting the list as the whole market. Each company comes with an \
            Apollo account id, which is what identifies it everywhere else.""")
    @RequiresWorkspaceAction(WorkspaceAction.PROJECT_BROWSE)
    public CompanyMatches searchCompanyUniverse(
            @ToolParam(required = false, description = "Country, spelled as describeMarket reports it")
            String country,
            @ToolParam(required = false, description = "Industry, spelled as describeMarket reports it")
            String industry,
            @ToolParam(required = false, description = "A word the company describes itself with")
            String keyword,
            @ToolParam(required = false, description = "All or part of a company name") String companyName,
            @ToolParam(required = false, description = "Fewest employees") Long minEmployees,
            @ToolParam(required = false, description = "Most employees") Long maxEmployees) {
        return market.matching(MarketQuery.scopeOf(country, industry, keyword, companyName,
                minEmployees, maxEmployees));
    }

    @Tool(description = """
            Report what the company universe holds: its industries grouped into sectors, the \
            countries it covers, the market segments, and the headcount and revenue bands — each \
            with how many companies it contains. Call this before searching when you are unsure how \
            an industry or a country is spelled, because a search only matches the exact spelling. \
            The counts are of the whole universe and are narrowed by nothing.""")
    @RequiresWorkspaceAction(WorkspaceAction.PROJECT_BROWSE)
    public MarketShape describeMarket() {
        return market.shape();
    }
}
