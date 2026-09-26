package app.lightmove.api.assistant.tool;

import app.lightmove.api.assistant.model.AssistantProposal;
import app.lightmove.api.assistant.model.ProposedCompany;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.strategy.service.StrategyService;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
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
            time an answer puts forward companies. Pass the Apollo account ids a search returned, and \
            the LinkedIn slugs of RESEARCHED companies lookUpCompaniesByName returned. The answer says \
            how many the card holds: companies the client has ruled off limits, or that were never \
            found, are dropped.""")
    public int proposeCompanies(
            @ToolParam(description = "One short line saying what these companies are") String title,
            @ToolParam(description = "Apollo account ids, and LinkedIn slugs of researched companies, from this conversation")
            List<String> companyIds,
            ToolContext toolContext) {
        AssistantToolContext context = AssistantToolContext.from(toolContext);
        List<String> asked = requested(companyIds);
        int step = context.recorder().startStep("Preparing " + asked.size() + " "
                + (asked.size() == 1 ? "company" : "companies"));
        AssistantProposal card = card(context, oneLine(title), asked);
        context.recorder().propose(card);
        context.recorder().finishStep(step,
                describeCard(card.companies().size(), asked.size() - card.companies().size()));
        return card.companies().size();
    }

    /** Flash sometimes answers straight after its lookups without proposing, and the card it describes must exist. */
    public void proposeWhatWasFound(AssistantToolContext context) {
        TurnRecorder recorder = context.recorder();
        List<String> found = Stream.concat(recorder.foundAccountIds().stream(),
                recorder.researched().keySet().stream()).toList();
        if (recorder.proposal() != null || found.isEmpty()) {
            return;
        }
        int step = recorder.startStep("Preparing the card");
        AssistantProposal card = card(context, "Companies found", found);
        recorder.propose(card);
        recorder.finishStep(step, describeCard(card.companies().size(), 0));
    }

    private static String describeCard(int carried, int leftOut) {
        String onCard = carried + " on the card";
        return leftOut == 0 ? onCard : onCard + ", " + leftOut + " left out (off limits or not found)";
    }

    private List<String> requested(List<String> companyIds) {
        if (companyIds == null) {
            return List.of();
        }
        return companyIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::strip)
                .distinct()
                .toList();
    }

    /**
     * Names and figures come from the universe row or the researched page, so the card never shows a
     * company the model made up: a slug counts only if this answer researched it.
     */
    private AssistantProposal card(AssistantToolContext context, String title, List<String> keys) {
        TurnRecorder recorder = context.recorder();
        Map<String, CapturedCompanyDetails> researched = recorder.researched();
        Map<String, String> operated = recorder.operatedBrands();
        Set<String> offLimits = Set.copyOf(
                strategies.scopeOf(context.workspaceId(), context.projectId()).offLimitsAccountIds());

        List<String> accountIds = keys.stream().filter(key -> !researched.containsKey(key)).toList();
        Stream<ProposedCompany> fromUniverse = accountIds.isEmpty() ? Stream.empty()
                : market.byAccountIds(accountIds).stream()
                        .filter(row -> !offLimits.contains(row.apolloAccountId()))
                        .map(row -> fromUniverse(row, operated.get(row.apolloAccountId())));
        Stream<ProposedCompany> fromLinkedIn = keys.stream()
                .filter(researched::containsKey)
                .map(slug -> fromLinkedIn(slug, researched.get(slug), operated.get(slug)));

        List<ProposedCompany> companies = Stream.concat(fromUniverse, fromLinkedIn)
                .sorted(Comparator.comparing(ProposedCompany::employees,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(maxRows)
                .toList();
        Map<String, CapturedCompanyDetails> carried = new LinkedHashMap<>();
        companies.stream()
                .filter(company -> company.apolloAccountId() == null)
                .forEach(company -> carried.put(company.linkedinSlug(), researched.get(company.linkedinSlug())));
        return new AssistantProposal(title, companies, carried);
    }

    private static ProposedCompany fromUniverse(CompanyRow row, String operates) {
        return new ProposedCompany(row.apolloAccountId(), null, row.companyName(), row.companyCountry(),
                row.numEmployees(), row.logoUrl(), operates);
    }

    private static ProposedCompany fromLinkedIn(String slug, CapturedCompanyDetails page, String operates) {
        return new ProposedCompany(null, slug, page.companyName(), page.companyCountry(),
                page.numEmployees(), page.logoUrl(), operates);
    }

    private static String oneLine(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        String flattened = title.replaceAll("\\s+", " ").strip();
        return flattened.length() <= MAX_TITLE ? flattened : flattened.substring(0, MAX_TITLE).strip();
    }
}
