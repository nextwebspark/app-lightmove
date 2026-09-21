package app.lightmove.api.assistant.tool;

import app.lightmove.api.assistant.model.AssistantProposal;
import app.lightmove.api.assistant.model.ProposalOrigin;
import app.lightmove.api.assistant.model.ProposedCompany;
import app.lightmove.api.assistant.service.AssistantEventSink;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.strategy.service.StrategyService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * How the assistant offers to file companies — and the only way it ever can.
 *
 * <p><b>It writes nothing.</b> Its whole effect is one {@code proposal} event; the rows exist in that
 * payload until a person accepts them. That is the third time this codebase has made the same call
 * deliberately — the importer writes nothing itself and is confirmed by a person, and the position
 * document's promised silent auto-fill shipped as review-then-accept — and the reason scales with
 * the actor: an agent filing forty companies into a client's mandate unprompted is that mistake
 * forty times over.
 *
 * <p><b>The first {@code WORK_EXECUTE} tool, which {@code docs/assistant-tools.md} anticipated.</b>
 * A proposal writes no rows, but it is the first half of a write and the only thing it is for is to
 * be accepted. {@code CLIENT} holds {@code WORK_VIEW}, so declaring that here would offer a client
 * representative a card whose every button refuses them — worse than not offering it. Both halves
 * declare the same action and agree.
 */
@Component
public class ProposalTools implements AssistantToolSubject {

    private static final int MAX_TITLE = 120;

    private static final TypeReference<Map<String, Object>> PAYLOAD = new TypeReference<>() {
    };

    private final ApolloCompanyQueryService market;
    private final StrategyService strategies;
    private final ObjectMapper json;
    private final int maxRows;

    public ProposalTools(ApolloCompanyQueryService market, StrategyService strategies,
                         ObjectMapper json, LightMoveProperties properties) {
        this.market = market;
        this.strategies = strategies;
        this.json = json;
        this.maxRows = properties.assistant().toolRowLimit();
    }

    @Tool(description = """
            Offer a set of companies for the user to file into a mandate, as a card they tick and \
            accept. This writes nothing: nothing reaches the mandate until a person presses a \
            button, and they choose the stage, so do not ask which stage they want. Propose only \
            companies you found through a search tool in this conversation, by the Apollo account \
            id that search gave you. The answer says how many the card actually carries and how \
            many were dropped — a company already ruled off limits, or one the universe no longer \
            has, is dropped — so describe the card by that number and never by what you asked \
            for.""")
    @RequiresProjectAction(ProjectAction.WORK_EXECUTE)
    public ProposalPlaced proposeCompanies(
            @ToolParam(description = "The mandate's id") String projectId,
            @ToolParam(description = "One short line saying what these companies are")
            String title,
            @ToolParam(description = "Apollo account ids, from a search in this conversation")
            List<String> apolloAccountIds,
            ToolContext toolContext) {
        AssistantToolCaller caller = ToolCallerContext.callerOf(toolContext);
        UUID mandate = UUID.fromString(projectId);
        List<String> asked = requested(apolloAccountIds);
        List<ProposedCompany> companies = resolve(caller.workspaceId(), mandate, asked);

        AssistantProposal proposal = new AssistantProposal(mandate, oneLine(title), companies);
        ToolCallerContext.sinkOf(toolContext).proposal(json.convertValue(proposal, PAYLOAD));

        return new ProposalPlaced(companies.size(), asked.size() - companies.size());
    }

    /**
     * Capped at the tool row limit for the reason that limit exists, and one more: a card is read by
     * a person, and the accept behind it files in one batch the bulk-add limit already bounds.
     */
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

    /**
     * Every field comes from the market row, never from the model.
     *
     * <p>A name the model supplied would render on the card as though we held it, which is the one
     * thing a provenance badge is there to prevent. Off-limits is applied here as well as at accept:
     * proposing a company the client has ruled out and then silently dropping it on the way in is
     * exactly the dishonest count this issue exists to avoid.
     */
    private List<ProposedCompany> resolve(UUID workspaceId, UUID projectId,
                                          List<String> apolloAccountIds) {
        if (apolloAccountIds.isEmpty()) {
            return List.of();
        }
        CompanyScope scope = strategies.scopeOf(workspaceId, projectId);
        Set<String> offLimits = Set.copyOf(scope.offLimitsAccountIds());
        AtomicInteger ref = new AtomicInteger();
        return market.byAccountIds(apolloAccountIds).stream()
                .filter(row -> !offLimits.contains(row.apolloAccountId()))
                .map(row -> universeRow("c" + ref.incrementAndGet(), row))
                .toList();
    }

    private static ProposedCompany universeRow(String ref, CompanyRow row) {
        return new ProposedCompany(ref, ProposalOrigin.UNIVERSE, row.apolloAccountId(),
                row.companyName(), row.companyCountry(), row.numEmployees());
    }

    /**
     * The model's own sentence, flattened and capped before it is stored.
     *
     * <p>The same treatment a member-typed position title gets before it reaches the system message:
     * this one is rendered as a card heading, and a newline in it is a line the panel did not lay
     * out.
     */
    private static String oneLine(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        String flattened = title.replaceAll("\\s+", " ").strip();
        return flattened.length() <= MAX_TITLE ? flattened : flattened.substring(0, MAX_TITLE).strip();
    }
}
