package app.lightmove.api.assistant.tool;

import app.lightmove.api.strategy.model.CompanyExclusion;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.model.NumericRange;
import java.util.List;

/**
 * Turns what a model asked for into the scope the market reads.
 *
 * <p><b>Headcount arrives as two numbers, not as a band.</b> {@code EmployeeBand} has eleven slugs
 * and {@code RevenueBand} its own set, and a model would have to be taught both to use either.
 * {@code CompanyScope} settles it: a {@code NumericRange} <i>takes precedence over its axis's band
 * list when set</i>, so a range is not a workaround for the bands but the other supported way of
 * saying it.
 *
 * <p>An omitted axis becomes an empty list, which {@code CompanyScope} defines as "no constraint on
 * this axis, never match nothing" — so a question that names only a country reads the whole of it.
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

    /**
     * Tightens a scope the mandate already defines, and can do nothing else: every axis it does not
     * take an argument for is copied through, so the off-limits list and the triage exclusion survive
     * whatever the model asked for.
     */
    static CompanyScope narrow(CompanyScope saved, String companyName, Long minEmployees,
                               Long maxEmployees) {
        NumericRange employees = rangeOf(minEmployees, maxEmployees);
        return new CompanyScope(saved.industries(), saved.keywords(), saved.marketSegments(),
                saved.countries(), saved.employeeBands(), saved.revenueBands(),
                employees == null ? saved.employeeRange() : employees, saved.revenueRange(),
                saved.offLimitsAccountIds(), saved.triagedExclusion(),
                companyName == null || companyName.isBlank() ? saved.nameQuery() : companyName);
    }

    private static List<String> listOf(String supplied) {
        return supplied == null || supplied.isBlank() ? List.of() : List.of(supplied.trim());
    }

    private static NumericRange rangeOf(Long min, Long max) {
        NumericRange range = new NumericRange(min, max);
        return range.isEmpty() ? null : range;
    }
}
