package app.lightmove.api.assistant.tool;

import app.lightmove.api.strategy.service.IndustryAdjacency;
import java.util.List;
import java.util.Locale;
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
    private final MarketTerms marketTerms;

    @Tool(description = """
            Search the company universe and return the largest matching companies, biggest first. \
            Every argument is optional and an omitted one places no constraint, so give only what \
            the question asks for. Country and industry may be written plainly: a country's name or \
            code ("UAE", "KSA"), a region ("GCC", "Middle East"), an industry ("banking", "oil and \
            gas") or a sector ("Technology"); interpretedAs says how a region or a sector was read. \
            If something is unrecognised nothing is searched — check its spelling with \
            describeMarket, then search again. The answer says how many companies matched in total \
            and how many are shown — when those differ you are seeing the largest, not all of them, \
            so narrow the search rather than reporting the list as the whole market. Each company \
            comes with an Apollo account id, which is what identifies it everywhere else. When one \
            industry is given, the answer also lists the industries adjacent to it.""")
    public CompanyMatches searchCompanyUniverse(
            @ToolParam(required = false, description = "Country, code, or GCC / Middle East") String country,
            @ToolParam(required = false, description = "Industry or sector") String industry,
            @ToolParam(required = false, description = "A word the company describes itself with")
            String keyword,
            @ToolParam(required = false, description = "All or part of a company name") String companyName,
            @ToolParam(required = false, description = "Fewest employees") Long minEmployees,
            @ToolParam(required = false, description = "Most employees") Long maxEmployees,
            ToolContext toolContext) {
        TurnRecorder recorder = AssistantToolContext.from(toolContext).recorder();
        ResolvedMarketTerms terms = marketTerms.resolve(country, industry);
        int step = recorder.startStep(describeSearch(terms.countryLabel(), terms.industryLabel(), keyword,
                companyName, minEmployees, maxEmployees));
        if (!terms.isFullyRecognised()) {
            recorder.finishStep(step, "Couldn't read " + String.join(" or ", terms.unrecognised()));
            return CompanyMatches.notSearched(terms.unrecognised());
        }
        CompanyMatches matches = market.matching(MarketQuery.scopeOf(terms.countries(), terms.industries(),
                        keyword, companyName, minEmployees, maxEmployees))
                .withAdjacentIndustries(terms.industries().size() == 1
                        ? adjacency.neighboursOf(terms.industries().getFirst()) : List.of())
                .interpretedAs(terms.interpretedAs());
        recorder.found(matches.companies().stream().map(MarketCompanySummary::apolloAccountId).toList());
        recorder.finishStep(step, describeMatches(matches));
        return matches;
    }

    @Tool(description = """
            Report what the company universe holds: its industries grouped into sectors, the \
            countries it covers, the market segments, and the headcount and revenue bands — each \
            with how many companies it contains. Call it only when a search reports something \
            unrecognised, or to compare sectors by size — a search reads plain names itself. The \
            counts are of the whole universe and are narrowed by nothing.""")
    public MarketShape describeMarket(ToolContext toolContext) {
        TurnRecorder recorder = AssistantToolContext.from(toolContext).recorder();
        int step = recorder.startStep("Checking how countries and industries are spelled");
        MarketShape shape = market.shape();
        recorder.finishStep(step, null);
        return shape;
    }

    /** "Searching retail companies named Lulu in United Arab Emirates with 500–5,000 staff". */
    static String describeSearch(String country, String industry, String keyword, String companyName,
                                 Long minEmployees, Long maxEmployees) {
        StringBuilder label = new StringBuilder("Searching ");
        if (hasText(industry)) {
            label.append(industry.strip()).append(' ');
        }
        if (hasText(keyword)) {
            label.append('"').append(keyword.strip()).append("\" ");
        }
        label.append("companies");
        if (hasText(companyName)) {
            label.append(" named ").append(companyName.strip());
        }
        if (hasText(country)) {
            label.append(" in ").append(country.strip());
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
        return matches.showing() < matches.matched()
                ? matched + ", showing the top " + matches.showing()
                : matched;
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
