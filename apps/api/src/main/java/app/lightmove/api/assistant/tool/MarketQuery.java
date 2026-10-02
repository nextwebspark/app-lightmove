package app.lightmove.api.assistant.tool;

import app.lightmove.api.common.industry.service.Industries;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.strategy.model.CompanyExclusion;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.model.NumericRange;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

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

    /**
     * The universe's own spelling of each country ("UAE" → "United Arab Emirates"), since the search
     * matches exactly; an unknown one is kept as asked. Spares the model a describeMarket round-trip.
     */
    static List<String> countriesOf(List<String> supplied) {
        return canonical(supplied, Countries::nameOf);
    }

    /** The universe's own label for each industry, however the model spelled or cased it. */
    static List<String> industriesOf(List<String> supplied) {
        return canonical(supplied, Industries::nameOf);
    }

    /** What neither catalog knows, so the model can check those spellings with describeMarket. */
    static List<String> unrecognised(List<String> countries, List<String> industries) {
        return Stream.concat(
                        cleaned(countries).stream().filter(country -> Countries.resolve(country).isEmpty()),
                        cleaned(industries).stream().filter(industry -> !Industries.isKnown(industry)))
                .toList();
    }

    private static List<String> canonical(List<String> supplied, UnaryOperator<String> spelling) {
        return cleaned(supplied).stream().map(spelling).distinct().toList();
    }

    private static List<String> listOf(String supplied) {
        return supplied == null || supplied.isBlank() ? List.of() : List.of(supplied.trim());
    }

    private static NumericRange rangeOf(Long min, Long max) {
        NumericRange range = new NumericRange(min, max);
        return range.isEmpty() ? null : range;
    }
}
