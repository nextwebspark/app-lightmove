package app.uncava.api.strategy.service;

import app.uncava.api.strategy.model.CompanyExclusion;
import app.uncava.api.strategy.model.CompanyScope;
import app.uncava.api.strategy.model.Strategy;
import app.uncava.api.strategy.model.StrategyFilter;

/**
 * Translates a mandate's saved {@link Strategy}, off-limits list included, into its universe scope —
 * the one translation both the Strategy results and the bulk triage writes use. The only
 * caller-supplied input is the name filter.
 */
public final class StrategyScope {

    private StrategyScope() {
    }

    public static CompanyScope of(Strategy strategy) {
        return of(strategy, null);
    }

    public static CompanyScope of(Strategy strategy, String nameQuery) {
        return of(strategy, nameQuery, CompanyExclusion.NONE);
    }

    /** The Strategy search's own use, so already-triaged companies stop reappearing; see {@link CompanyScope}. */
    public static CompanyScope of(Strategy strategy, String nameQuery, CompanyExclusion triagedExclusion) {
        StrategyFilter filter = strategy.getFilter();
        return new CompanyScope(
                filter.industries(),
                filter.keywords(),
                filter.marketSegments(),
                filter.countries(),
                filter.employeeBands(),
                filter.revenueBands(),
                filter.employeeRange(),
                filter.revenueRange(),
                strategy.offLimitsAccountIds(),
                triagedExclusion,
                nameQuery);
    }
}
