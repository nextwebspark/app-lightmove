package app.lightmove.api.assistant.tool;

import app.lightmove.api.strategy.model.CompanyRow;
import java.util.List;

/**
 * What a search found, how much of it is here, and the industries beside the one searched — carried
 * on the result because a model told to ask for them separately does not reliably ask.
 *
 *
 * <p>The total is not decoration. A model handed twenty-five rows with no count reasons as though
 * those are the market — it totals them, calls them "the" operators, and a client-facing sentence
 * inherits the mistake. Saying 1,284 matched and 25 are shown is what lets it narrow instead, and
 * is what {@code Assistant.dc.html} writes into its own trace line.
 */
public record CompanyMatches(long matched, int showing, List<MarketCompanySummary> companies,
                             List<String> adjacentIndustries) {

    static CompanyMatches of(long matched, List<CompanyRow> page) {
        return new CompanyMatches(matched, page.size(),
                page.stream().map(MarketCompanySummary::of).toList(), List.of());
    }

    CompanyMatches withAdjacentIndustries(List<String> adjacent) {
        return new CompanyMatches(matched, showing, companies, adjacent);
    }
}
