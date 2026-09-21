package app.lightmove.api.assistant.tool;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.strategy.constant.CompanySortField;
import app.lightmove.api.strategy.constant.SortDirection;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import org.springframework.stereotype.Component;

/**
 * One capped, counted read of the company universe, and the vocabulary it has to be asked in.
 *
 * <p>Its own collaborator rather than a method on a tool class because two tools read the same
 * market: {@link CompanySearchTools} over the universe as it is, {@link StrategyTools} through one
 * mandate's saved filter. Sharing it here keeps both answering with the same cap and the same order,
 * and keeps either tool from depending on the other.
 */
@Component
class MarketSearch {

    /** Biggest first: "the top operators in X" is a headcount question, and the market has no rank. */
    private static final CompanySortField BY_SIZE = CompanySortField.EMPLOYEES;

    private final ApolloCompanyQueryService companies;
    private final int maxRows;

    MarketSearch(ApolloCompanyQueryService companies, LightMoveProperties properties) {
        this.companies = companies;
        this.maxRows = properties.assistant().toolRowLimit();
    }

    /**
     * The count is a second query and worth it: without it the answer cannot say whether the page is
     * the market or a corner of it, which is the one thing a model reading a capped list gets wrong.
     */
    CompanyMatches matching(CompanyScope scope) {
        return CompanyMatches.of(companies.count(scope),
                companies.search(scope, BY_SIZE, SortDirection.DESC, 0, maxRows));
    }

    MarketShape shape() {
        return new MarketShape(companies.sectorGroups(),
                companies.countByCountry(CompanyScope.unfiltered(), maxRows),
                companies.marketSegmentFacets(), companies.employeeBandFacets(),
                companies.revenueBandFacets());
    }
}
