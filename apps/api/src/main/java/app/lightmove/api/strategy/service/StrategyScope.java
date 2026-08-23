package app.lightmove.api.strategy.service;

import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.model.Strategy;
import app.lightmove.api.strategy.model.StrategyFilter;

/**
 * Translates a mandate's saved {@link Strategy} into the universe scope it defines.
 *
 * <p>Two screens resolve the same criteria — the Strategy results table and the mandate report — and
 * two copies of this translation would let them quietly disagree about what the consultant asked for.
 * Unlike the version this replaces, they now also read the same table, so they agree on the answer
 * and not merely on the question.
 *
 * <p>The one thing the filter cannot state about itself is the off-limits list: the filter is the
 * sidebar's selection, the strategy holds the barred companies, and only here are both in hand.
 *
 * <p>Nothing here reads a request parameter. A mandate's chosen scope is stored, team-only content;
 * the only thing a caller ever supplies is the name filter passed through to {@code nameQuery}.
 */
public final class StrategyScope {

    private StrategyScope() {
    }

    /** The universe scope this strategy defines. */
    public static CompanyScope of(Strategy strategy) {
        return of(strategy, null);
    }

    /** The same scope, narrowed by a caller's name filter. */
    public static CompanyScope of(Strategy strategy, String nameQuery) {
        StrategyFilter filter = strategy.getFilter();
        return new CompanyScope(
                filter.industries(),
                filter.marketSegments(),
                filter.countries(),
                filter.employeeBands(),
                filter.revenueBands(),
                filter.employeeRange(),
                filter.revenueRange(),
                strategy.offLimitsAccountIds(),
                nameQuery);
    }
}
