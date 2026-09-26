package app.lightmove.api.assistant.tool;

import app.lightmove.api.strategy.model.CompanyExclusion;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.model.NumericRange;
import java.util.List;

/**
 * Turns what a model asked for into the scope the market reads. Headcount arrives as a
 * {@code NumericRange} rather than a band, so the model need not learn the band slugs; an omitted axis
 * is an empty list, meaning no constraint.
 */
final class MarketQuery {

    private MarketQuery() {
    }

    static CompanyScope scopeOf(String country, String industry, String keyword, String companyName,
                                Long minEmployees, Long maxEmployees) {
        return new CompanyScope(listOf(industry), listOf(keyword), List.of(), listOf(country),
                List.of(), List.of(), rangeOf(minEmployees, maxEmployees), null,
                List.of(), CompanyExclusion.NONE, companyName);
    }

    private static List<String> listOf(String supplied) {
        return supplied == null || supplied.isBlank() ? List.of() : List.of(supplied.trim());
    }

    private static NumericRange rangeOf(Long min, Long max) {
        NumericRange range = new NumericRange(min, max);
        return range.isEmpty() ? null : range;
    }
}
