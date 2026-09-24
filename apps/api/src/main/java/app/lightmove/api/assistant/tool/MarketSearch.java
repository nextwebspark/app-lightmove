package app.lightmove.api.assistant.tool;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.strategy.constant.CompanySortField;
import app.lightmove.api.strategy.constant.SortDirection;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import org.springframework.stereotype.Component;

/** One capped, counted read of the company universe, and the vocabulary it has to be asked in. */
@Component
class MarketSearch {

    /** Biggest first: "the top operators in X" is a headcount question, and the market has no rank. */
    private static final CompanySortField BY_SIZE = CompanySortField.EMPLOYEES;

    private final ApolloCompanyQueryService companies;
    private final int maxRows;
    private final int maxVocabulary;

    MarketSearch(ApolloCompanyQueryService companies, LightMoveProperties properties) {
        this.companies = companies;
        this.maxRows = properties.assistant().toolRowLimit();
        this.maxVocabulary = properties.assistant().vocabularyLimit();
    }

    /**
     * The count is a second query and worth it: without it the answer cannot say whether the page is
     * the market or a corner of it, which is the one thing a model reading a capped list gets wrong.
     */
    CompanyMatches matching(CompanyScope scope) {
        return CompanyMatches.of(companies.count(scope),
                companies.search(scope, BY_SIZE, SortDirection.DESC, 0, maxRows));
    }

    /**
     * The countries take the vocabulary limit and not the row limit. Every other axis here ships
     * whole — the sectors, the segments and both band sets are closed lists — and the countries are
     * the one that goes through a {@code LIMIT}, so the row limit would silently make this the top
     * twenty-five by company count while the search tools call its spellings authoritative.
     */
    MarketShape shape() {
        return new MarketShape(companies.sectorGroups(),
                companies.countByCountry(CompanyScope.unfiltered(), maxVocabulary),
                companies.marketSegmentFacets(), companies.employeeBandFacets(),
                companies.revenueBandFacets());
    }
}
