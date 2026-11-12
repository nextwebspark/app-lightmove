package app.lightmove.api.enrichment.sourcing.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Where a search looks and how much it may buy, held to what the panel offers whoever sends them. */
class SearchCriteriaTest {

    private static final SearchWords FINANCE = SearchWords.defaultFor("Group CFO", null);

    @Test
    @DisplayName("countries are two-letter codes, once each; a profiles-per-company the panel does not offer is three")
    void cleansItself() {
        SearchCriteria criteria = new SearchCriteria(FINANCE, List.of("ae", " SA ", "AE", "Gulf", "x"), 7);

        assertThat(criteria.countryCodes()).containsExactly("AE", "SA");
        assertThat(criteria.perCompany()).isEqualTo(3);
        assertThat(new SearchCriteria(FINANCE, null, 10).perCompany()).isEqualTo(10);
    }

    @Test
    @DisplayName("each company is searched in the search's countries and its own; a search naming none looks anywhere")
    void eachCompanyAddsItsOwnCountry() {
        SearchCriteria gulf = new SearchCriteria(FINANCE, List.of("AE", "SA"), 3);
        SourcingCompany cairo = company("EG");

        assertThat(gulf.countriesFor(cairo)).containsExactly("AE", "SA", "EG");
        assertThat(gulf.countriesFor(company("SA"))).containsExactly("AE", "SA");
        assertThat(gulf.countriesFor(company(null))).containsExactly("AE", "SA");
        assertThat(new SearchCriteria(FINANCE, List.of(), 3).countriesFor(cairo)).isEmpty();
        assertThat(SearchCriteria.regionOf("AE", List.of("AE", "SA"))).containsExactly("AE", "SA");
        assertThat(SearchCriteria.regionOf(null, List.of("AE", "SA"))).isEmpty();
    }

    private static SourcingCompany company(String countryCode) {
        return new SourcingCompany(UUID.randomUUID(), "Company", "company", null, null, countryCode);
    }
}
