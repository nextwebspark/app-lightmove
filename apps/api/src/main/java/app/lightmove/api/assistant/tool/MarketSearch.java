package app.lightmove.api.assistant.tool;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.strategy.constant.CompanySortField;
import app.lightmove.api.strategy.constant.SortDirection;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.strategy.service.UniverseFacets;
import org.springframework.stereotype.Component;

/** One capped, counted read of the company universe, and the vocabulary it has to be asked in. */
@Component
class MarketSearch {

    /** Biggest first: "the top operators in X" is a headcount question, and the market has no rank. */
    private static final CompanySortField BY_SIZE = CompanySortField.EMPLOYEES;

    private final ApolloCompanyQueryService companies;
    private final UniverseFacets facets;
    private final int maxRows;
    private final int maxVocabulary;

    MarketSearch(ApolloCompanyQueryService companies, UniverseFacets facets, LightMoveProperties properties) {
        this.companies = companies;
        this.facets = facets;
        this.maxRows = properties.assistant().toolRowLimit();
        this.maxVocabulary = properties.assistant().vocabularyLimit();
    }

    /** The count tells the model whether the capped page is the market or a corner of it. */
    CompanyMatches matching(CompanyScope scope) {
        return CompanyMatches.of(companies.count(scope),
                companies.search(scope, BY_SIZE, SortDirection.DESC, 0, maxRows));
    }

    /**
     * The countries take the vocabulary limit, not the row limit, which would silently cut the list the
     * search tools call authoritative to the top twenty-five.
     */
    MarketShape shape() {
        return new MarketShape(facets.sectorGroups(), facets.countries(maxVocabulary),
                facets.marketSegments(), facets.employeeBands(), facets.revenueBands());
    }
}
