package app.lightmove.api.assistant.tool;

import app.lightmove.api.strategy.service.IndustryAdjacency;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** The two tools that read the company universe. */
@Component
@RequiredArgsConstructor
public class CompanySearchTools {

    private final MarketSearch market;
    private final IndustryAdjacency adjacency;
    private final TriageCompanyReadService triaged;

    @Tool(description = """
            Search the company universe and return the largest matching companies, biggest first. \
            Every argument is optional and an omitted one places no constraint, so give only what \
            the question asks for. Several countries or several industries may be given in one \
            search — a company matches any of them — so ask once for "retail and hospitality in the \
            UAE and Saudi Arabia" rather than once per pair. Common spellings of a country or an \
            industry are understood ("UAE", "KSA", "Retail"); any the universe does not know come \
            back as unrecognisedSpellings — check those with describeMarket. The answer says how \
            many companies matched in \
            total and how many are shown — when those differ you are seeing the largest, not all of \
            them, so narrow the search rather than reporting the list as the whole market. Each \
            company comes with an Apollo account id, which is what identifies it everywhere else, \
            and a mandateStage when this mandate has already filed it (inUniverse, shortlisted or \
            declined). When an industry is given, the answer also lists the industries adjacent to it.""")
    public CompanyMatches searchCompanyUniverse(
            @ToolParam(required = false, description = "Countries; at most five")
            List<String> countries,
            @ToolParam(required = false, description = "Industries; at most five")
            List<String> industries,
            @ToolParam(required = false, description = "A word the company describes itself with")
            String keyword,
            @ToolParam(required = false, description = "All or part of a company name") String companyName,
            @ToolParam(required = false, description = "Fewest employees") Long minEmployees,
            @ToolParam(required = false, description = "Most employees") Long maxEmployees,
            ToolContext toolContext) {
        AssistantToolContext context = AssistantToolContext.from(toolContext);
        TurnRecorder recorder = context.recorder();
        List<String> askedCountries = MarketQuery.countriesOf(countries);
        List<String> askedIndustries = MarketQuery.industriesOf(industries);
        int step = recorder.startStep(describeSearch(askedCountries, askedIndustries, keyword, companyName,
                minEmployees, maxEmployees));
        CompanyMatches found = market.matching(MarketQuery.scopeOf(askedCountries, askedIndustries, keyword,
                companyName, minEmployees, maxEmployees));
        CompanyMatches matches = found
                .withMandateStages(triaged.stagesOf(context.workspaceId(), context.projectId(),
                        found.companies().stream().map(MarketCompanySummary::apolloAccountId).toList(),
                        found.companies().stream().map(MarketCompanySummary::companyName).toList()))
                .withAdjacentIndustries(adjacentTo(askedIndustries))
                .withUnrecognisedSpellings(MarketQuery.unrecognised(askedCountries, askedIndustries));
        recorder.found(matches.companies().stream().map(MarketCompanySummary::apolloAccountId).toList());
        if (!hasText(companyName)) {
            recorder.searchedMarket(new MarketAsk(askedCountries, askedIndustries, keyword, minEmployees,
                    maxEmployees));
        }
        recorder.finishStep(step, describeMatches(matches));
        return matches;
    }

    @Tool(description = """
            Report what the company universe holds: its industries grouped into sectors, the \
            countries it covers, the market segments, and the headcount and revenue bands — each \
            with how many companies it contains. Call this before searching when you are unsure how \
            an industry or a country is spelled, because a search only matches the exact spelling. \
            The counts are of the whole universe and are narrowed by nothing.""")
    public MarketShape describeMarket(ToolContext toolContext) {
        TurnRecorder recorder = AssistantToolContext.from(toolContext).recorder();
        int step = recorder.startStep("Checking how countries and industries are spelled");
        MarketShape shape = market.shape();
        recorder.finishStep(step, null);
        return shape;
    }

    /** Each industry's neighbours, once, leaving out the industries the search already covers. */
    private List<String> adjacentTo(List<String> industries) {
        Set<String> asked = industries.stream()
                .map(industry -> industry.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return industries.stream()
                .flatMap(industry -> adjacency.neighboursOf(industry).stream())
                .filter(neighbour -> !asked.contains(neighbour.toLowerCase(Locale.ROOT)))
                .distinct()
                .toList();
    }

    /** "Searching retail or hospitality companies named Lulu in United Arab Emirates with 500–5,000 staff". */
    static String describeSearch(List<String> countries, List<String> industries, String keyword,
                                 String companyName, Long minEmployees, Long maxEmployees) {
        StringBuilder label = new StringBuilder("Searching ");
        if (!industries.isEmpty()) {
            label.append(String.join(" or ", industries)).append(' ');
        }
        if (hasText(keyword)) {
            label.append('"').append(keyword.strip()).append("\" ");
        }
        label.append("companies");
        if (hasText(companyName)) {
            label.append(" named ").append(companyName.strip());
        }
        if (!countries.isEmpty()) {
            label.append(" in ").append(String.join(" or ", countries));
        }
        String staff = describeStaff(minEmployees, maxEmployees);
        if (staff != null) {
            label.append(" with ").append(staff);
        }
        return label.toString();
    }

    static String describeMatches(CompanyMatches matches) {
        if (matches.matched() == 0) {
            return "No companies matched";
        }
        String matched = String.format(Locale.ROOT, "%,d matched", matches.matched());
        String described = matches.showing() < matches.matched()
                ? matched + ", showing the top " + matches.showing()
                : matched;
        long held = matches.alreadyInMandate();
        return held == 0 ? described : described + " · " + held + " already in the mandate";
    }

    private static String describeStaff(Long min, Long max) {
        if (min != null && max != null) {
            return String.format(Locale.ROOT, "%,d–%,d staff", min, max);
        }
        if (min != null) {
            return String.format(Locale.ROOT, "at least %,d staff", min);
        }
        return max == null ? null : String.format(Locale.ROOT, "up to %,d staff", max);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
