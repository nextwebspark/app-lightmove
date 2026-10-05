package app.lightmove.api.assistant.tool;

import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.triagecompany.model.MandateStages;
import java.util.List;

/**
 * What a search found, with the total and the neighbouring industries on the result. The total
 * matters: handed 25 rows with no count, a model treats them as the whole market. Unrecognised
 * spellings are the countries and industries the universe has no name for, which matched nothing.
 */
public record CompanyMatches(long matched, int showing, List<MarketCompanySummary> companies,
                             List<String> adjacentIndustries, List<String> unrecognisedSpellings) {

    static CompanyMatches of(long matched, List<CompanyRow> page) {
        return of(matched, page.size(), page.stream().map(MarketCompanySummary::of).toList());
    }

    /** The industry and spelling lists start empty and are set by name, so the two cannot be swapped. */
    static CompanyMatches of(long matched, int showing, List<MarketCompanySummary> companies) {
        return new CompanyMatches(matched, showing, companies, List.of(), List.of());
    }

    CompanyMatches withAdjacentIndustries(List<String> adjacent) {
        return new CompanyMatches(matched, showing, companies, adjacent, unrecognisedSpellings);
    }

    CompanyMatches withUnrecognisedSpellings(List<String> unrecognised) {
        return new CompanyMatches(matched, showing, companies, adjacentIndustries, unrecognised);
    }

    CompanyMatches withMandateStages(MandateStages stages) {
        return new CompanyMatches(matched, showing, companies.stream()
                .map(company -> company.inMandateAs(
                        stages.stageTokenOf(company.apolloAccountId(), company.companyName())))
                .toList(), adjacentIndustries, unrecognisedSpellings);
    }

    long alreadyInMandate() {
        return companies.stream().filter(company -> company.mandateStage() != null).count();
    }
}
