package app.lightmove.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.common.location.service.Countries;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The country vocabulary as the SPA reads it, and the one invariant a unit test cannot check:
 * whether every country the universe carries resolves through the catalog.
 *
 * <p>An unresolvable country does not fail loudly — the Strategy filter matches
 * {@code company_country} exactly, so a spelling the catalog cannot fold becomes a chip that returns
 * nothing, which looks identical to a market with no companies in it. The universe has already been
 * respelled once, from "Turkey" to "Türkiye", so this is the assertion that would catch the next one.
 */
@IntegrationTest
class CountryVocabularyIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;

    private ApolloUniverse universe;

    @BeforeEach
    void freshUniverse() {
        universe = new ApolloUniverse(db);
        universe.reset();
    }

    @Test
    @DisplayName("every country the universe carries resolves to one code and one name")
    void everyLiveCountryResolves() {
        // Seeded, then read back out of the table rather than off the list that seeded it — the
        // point is the SELECT. Asserting against the same literals would pass with an empty universe
        // and would never catch the next respelling, which is the only thing this test is for.
        List<String> seeded = List.of("United Arab Emirates", "Saudi Arabia", "Egypt", "Türkiye",
                "Qatar", "Kuwait", "Oman", "Bahrain", "United States", "Brazil", "Jordan", "China");
        for (String country : seeded) {
            universe.company("a-" + country, country + " Co").country(country).employees(10).insert();
        }

        List<String> live = db.queryForList(
                "SELECT DISTINCT company_country FROM app_lm_apollo_companies "
                        + "WHERE company_country IS NOT NULL AND company_country <> ''", String.class);

        assertThat(live).hasSameSizeAs(seeded);
        for (String country : live) {
            assertThat(Countries.resolve(country))
                    .as("the universe carries '%s' and the catalog must know it", country)
                    .isPresent();
            // And it must round-trip: a canonical name the catalog rewrote would stop matching the
            // column the Strategy filter compares it against.
            assertThat(Countries.nameOf(country)).isEqualTo(country);
        }
    }

    @Test
    @DisplayName("the vocabulary carries the world, and the markets are counted off the universe")
    void vocabularyAndMarketsAreServed() throws Exception {
        String admin = adminOf("Country Vocabulary Firm");
        universe.company("a1", "One").country("United Arab Emirates").employees(10).insert();
        universe.company("a2", "Two").country("United Arab Emirates").employees(10).insert();
        universe.company("a3", "Three").country("Saudi Arabia").employees(10).insert();

        mvc.perform(get("/api/v1/countries").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                // Every country a mandate can map somebody in, not only the markets it searches.
                .andExpect(jsonPath("$.countries[?(@.code == 'IE')].name").value("Ireland"))
                .andExpect(jsonPath("$.countries[?(@.code == 'AE')].name").value("United Arab Emirates"))
                // Largest market first, so a chip can never offer a market the pipeline has not loaded.
                .andExpect(jsonPath("$.markets[0]").value("United Arab Emirates"))
                .andExpect(jsonPath("$.markets[1]").value("Saudi Arabia"))
                .andExpect(jsonPath("$.markets.length()").value(2));
    }

    private String adminOf(String workspaceName) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), workspaceName);
        return login("alok@" + domain);
    }
}
