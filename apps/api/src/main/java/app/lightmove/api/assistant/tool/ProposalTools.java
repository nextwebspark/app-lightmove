package app.lightmove.api.assistant.tool;

import app.lightmove.api.assistant.model.AssistantProposal;
import app.lightmove.api.assistant.model.ProposedCompany;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.strategy.service.StrategyService;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import app.lightmove.api.triagecompany.model.MandateStages;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final TriageCompanyReadService triaged;
    private final int maxRows;

    public ProposalTools(ApolloCompanyQueryService market, StrategyService strategies,
                         TriageCompanyReadService triaged, LightMoveProperties properties) {
        this.market = market;
        this.strategies = strategies;
        this.triaged = triaged;
        this.maxRows = properties.assistant().toolRowLimit();
    }

    @Tool(description = """
            List companies below the answer, for the user to tick and file into the mandate. Call it \
            every time an answer puts forward companies. Pass the Apollo account ids a search returned, \
            and the LinkedIn slugs of RESEARCHED companies lookUpCompaniesByName returned, in this \
            answer or in an earlier suggested_companies block of this chat. The answer says how many \
            are suggested and how many of those the mandate has already filed (shown with their stage, \
            not offered again); companies the client has ruled off limits, or that were never found, \
            are left out.""")
    public CardResult proposeCompanies(
            @ToolParam(description = "One short line saying what these companies are") String title,
            @ToolParam(description = "Apollo account ids, and LinkedIn slugs of researched companies, from this chat")
            List<String> companyIds,
            ToolContext toolContext) {
        AssistantToolContext context = AssistantToolContext.from(toolContext);
        List<String> asked = requested(companyIds);
        int step = context.recorder().startStep("Preparing " + asked.size() + " "
                + (asked.size() == 1 ? "company" : "companies"));
        AssistantProposal card = card(context, oneLine(title), asked);
        context.recorder().propose(card);
        CardResult result = resultOf(card, asked.size());
        context.recorder().finishStep(step, describeCard(result));
        return result;
    }

    /** Flash sometimes answers straight after its lookups without proposing, and the card it describes must exist. */
    public void proposeWhatWasFound(AssistantToolContext context) {
        TurnRecorder recorder = context.recorder();
        List<String> found = Stream.concat(recorder.foundAccountIds().stream(),
                recorder.researched().keySet().stream()).toList();
        if (recorder.proposal() != null || found.isEmpty()) {
            return;
        }
        int step = recorder.startStep("Preparing the suggested companies");
        AssistantProposal card = card(context, "Companies found", found);
        recorder.propose(card);
        recorder.finishStep(step, describeCard(resultOf(card, card.companies().size())));
    }

    private static CardResult resultOf(AssistantProposal card, int asked) {
        int held = (int) card.companies().stream().filter(ProposedCompany::alreadyInMandate).count();
        return new CardResult(card.companies().size(), held, Math.max(0, asked - card.companies().size()));
    }

    /** "8 suggested, 2 already in the mandate, 1 left out (off limits or not found)". */
    static String describeCard(CardResult result) {
        StringBuilder described = new StringBuilder(result.suggested() + " suggested");
        if (result.alreadyInMandate() > 0) {
            described.append(", ").append(result.alreadyInMandate()).append(" already in the mandate");
        }
        if (result.leftOut() > 0) {
            described.append(", ").append(result.leftOut()).append(" left out (off limits or not found)");
        }
        return described.toString();
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
     * company the model made up: a slug counts only if this answer, or an earlier card of the chat,
     * researched it. A company the mandate already holds stays on the card with its stage, after the
     * new ones, so the consultant sees the whole answer and what is already decided.
     */
    private AssistantProposal card(AssistantToolContext context, String title, List<String> keys) {
        TurnRecorder recorder = context.recorder();
        Map<String, CapturedCompanyDetails> researched = recorder.proposablePages();
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

        List<ProposedCompany> found = Stream.concat(fromUniverse, fromLinkedIn).toList();
        MandateStages stages = triaged.stagesOf(context.workspaceId(), context.projectId(),
                found.stream().map(ProposedCompany::apolloAccountId).filter(Objects::nonNull).toList(),
                found.stream().map(ProposedCompany::companyName).toList());
        List<ProposedCompany> companies = found.stream()
                .map(company -> company.inMandateAs(
                        stages.stageTokenOf(company.apolloAccountId(), company.companyName())))
                .sorted(Comparator.comparing(ProposedCompany::alreadyInMandate)
                        .thenComparing(ProposedCompany::employees,
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
                row.numEmployees(), row.logoUrl(), operates, null);
    }

    private static ProposedCompany fromLinkedIn(String slug, CapturedCompanyDetails page, String operates) {
        return new ProposedCompany(null, slug, page.companyName(), page.companyCountry(),
                page.numEmployees(), page.logoUrl(), operates, null);
    }

    private static String oneLine(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        String flattened = title.replaceAll("\\s+", " ").strip();
        return flattened.length() <= MAX_TITLE ? flattened : flattened.substring(0, MAX_TITLE).strip();
    }
}
