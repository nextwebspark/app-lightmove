package app.lightmove.api.assistant.tool;

import app.lightmove.api.strategy.model.CompanyRow;
import java.util.List;

/**
 * What a search found, with the total and the neighbouring industries on the result. The total
 * matters: handed 25 rows with no count, a model treats them as the whole market.
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
