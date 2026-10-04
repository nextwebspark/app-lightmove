package app.lightmove.api.assistant.tool;

import app.lightmove.api.strategy.model.CompanyExclusion;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.model.NumericRange;
import java.util.List;
import java.util.Objects;

/**
 * Turns what a model asked for into the scope the market reads. Headcount arrives as a
 * {@code NumericRange} rather than a band, so the model need not learn the band slugs; an omitted axis
 * is an empty list, meaning no constraint.
 */
final class MarketQuery {

    /** Countries or industries one search takes — enough for a region, not a tour of the universe. */
    static final int MAX_VALUES_PER_AXIS = 5;

    private MarketQuery() {
    }

    static CompanyScope scopeOf(List<String> countries, List<String> industries, String keyword,
                                String companyName, Long minEmployees, Long maxEmployees) {
        return new CompanyScope(cleaned(industries), listOf(keyword), List.of(), cleaned(countries),
                List.of(), List.of(), rangeOf(minEmployees, maxEmployees), null,
                List.of(), CompanyExclusion.NONE, companyName);
    }

    /** Stripped, blanks dropped, repeats dropped, capped — what the search and its label both read. */
    static List<String> cleaned(List<String> supplied) {
        if (supplied == null) {
            return List.of();
        }
        return supplied.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .distinct()
                .limit(MAX_VALUES_PER_AXIS)
                .toList();
    }

    private static List<String> listOf(String supplied) {
        return supplied == null || supplied.isBlank() ? List.of() : List.of(supplied.trim());
    }

    private static NumericRange rangeOf(Long min, Long max) {
        NumericRange range = new NumericRange(min, max);
        return range.isEmpty() ? null : range;
    }
}
