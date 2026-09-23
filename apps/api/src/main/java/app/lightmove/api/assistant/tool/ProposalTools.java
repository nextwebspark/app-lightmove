package app.lightmove.api.assistant.tool;

import app.lightmove.api.assistant.model.AssistantProposal;
import app.lightmove.api.assistant.model.ProposedCompany;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.strategy.service.StrategyService;
import java.util.List;
import java.util.Set;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Puts companies in front of the user as a card they tick and file. It writes nothing to the mandate;
 * the card is saved with the answer and filed only when a person presses a stage button.
 */
@Component
public class ProposalTools {

    private static final int MAX_TITLE = 120;

    private final ApolloCompanyQueryService market;
    private final StrategyService strategies;
    private final int maxRows;

    public ProposalTools(ApolloCompanyQueryService market, StrategyService strategies,
                         LightMoveProperties properties) {
        this.market = market;
        this.strategies = strategies;
        this.maxRows = properties.assistant().toolRowLimit();
    }

    @Tool(description = """
            Show companies to the user as a card they can tick and add to the mandate. Call it every \
            time an answer puts forward companies a search found. Pass the Apollo account ids the \
            search returned. The answer says how many the card holds: companies the client has ruled \
            off limits, or that the universe no longer has, are dropped.""")
    public int proposeCompanies(
            @ToolParam(description = "One short line saying what these companies are") String title,
            @ToolParam(description = "Apollo account ids from a search in this conversation")
            List<String> apolloAccountIds,
            ToolContext toolContext) {
        AssistantToolContext context = AssistantToolContext.from(toolContext);
        List<String> asked = requested(apolloAccountIds);
        int step = context.recorder().startStep("Preparing " + asked.size() + " "
                + (asked.size() == 1 ? "company" : "companies"));
        List<ProposedCompany> companies = resolve(context, asked);
        context.recorder().propose(new AssistantProposal(oneLine(title), companies));
        context.recorder().finishStep(step, describeCard(companies.size(), asked.size() - companies.size()));
        return companies.size();
    }

    private static String describeCard(int carried, int leftOut) {
        String onCard = carried + " on the card";
        return leftOut == 0 ? onCard
                : onCard + ", " + leftOut + " left out (off limits or no longer in the universe)";
    }

    private List<String> requested(List<String> apolloAccountIds) {
        if (apolloAccountIds == null) {
            return List.of();
        }
        return apolloAccountIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .limit(maxRows)
                .toList();
    }

    /** Names and figures come from the universe row, so the card never shows a company the model made up. */
    private List<ProposedCompany> resolve(AssistantToolContext context, List<String> apolloAccountIds) {
        if (apolloAccountIds.isEmpty()) {
            return List.of();
        }
        Set<String> offLimits = Set.copyOf(
                strategies.scopeOf(context.workspaceId(), context.projectId()).offLimitsAccountIds());
        return market.byAccountIds(apolloAccountIds).stream()
                .filter(row -> !offLimits.contains(row.apolloAccountId()))
                .map(ProposalTools::toProposed)
                .toList();
    }

    private static ProposedCompany toProposed(CompanyRow row) {
        return new ProposedCompany(row.apolloAccountId(), row.companyName(), row.companyCountry(),
                row.numEmployees(), row.logoUrl());
    }

    private static String oneLine(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        String flattened = title.replaceAll("\\s+", " ").strip();
        return flattened.length() <= MAX_TITLE ? flattened : flattened.substring(0, MAX_TITLE).strip();
    }
}
