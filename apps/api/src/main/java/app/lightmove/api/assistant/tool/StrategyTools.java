package app.lightmove.api.assistant.tool;

import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.service.StrategyService;
import java.util.UUID;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The same market, read through one mandate's saved filter.
 *
 * <p>The filter is the consultant's own statement of where this search is being run, and it carries
 * the mandate's off-limits list with it — companies a client has ruled out, which must not be
 * proposed however well they match. So the scope comes from {@code StrategyService} and the model
 * may only narrow it further; there is no argument in which it could widen one axis or drop the
 * exclusions.
 *
 * <p>{@link StrategyService#untriagedScopeOf} rather than {@code scopeOf}, so the answer is the
 * Strategy screen's own list: a company the mandate has already filed is not something else that is
 * out there, and proposing it back is how an assistant talks a consultant into work they have done.
 */
@Component
public class StrategyTools implements AssistantToolSubject {

    private final StrategyService strategies;
    private final MarketSearch market;

    public StrategyTools(StrategyService strategies, MarketSearch market) {
        this.strategies = strategies;
        this.market = market;
    }

    @Tool(description = """
            Find companies a mandate has not looked at yet: the company universe read through its \
            own saved filter — industries, countries, segments and size bands — with the companies \
            it has ruled off limits and the ones it has already filed at any stage both left out. \
            This is the Strategy screen's own list, so use it for "what else is out there for this \
            search", listMandateCompanies for what the mandate has already taken, and \
            searchCompanyUniverse for a market question the mandate's filter would wrongly narrow. \
            The optional arguments narrow the saved filter further and cannot widen it. The answer \
            says how many matched in total and how many are shown.""")
    @RequiresProjectAction(ProjectAction.WORK_VIEW)
    public CompanyMatches searchMandateFilter(
            @ToolParam(description = "The mandate's id") String projectId,
            @ToolParam(required = false, description = "All or part of a company name") String companyName,
            @ToolParam(required = false, description = "Fewest employees") Long minEmployees,
            @ToolParam(required = false, description = "Most employees") Long maxEmployees,
            ToolContext toolContext) {
        AssistantToolCaller caller = ToolCallerContext.callerOf(toolContext);
        CompanyScope mandateScope = strategies.untriagedScopeOf(caller.workspaceId(),
                UUID.fromString(projectId));
        return market.matching(
                MarketQuery.narrow(mandateScope, companyName, minEmployees, maxEmployees));
    }
}
