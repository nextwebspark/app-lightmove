package app.lightmove.api.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import app.lightmove.api.strategy.service.UniverseReloadWatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * That a reloaded universe is noticed without anyone being told, which is the whole reason the caches
 * are allowed TTLs measured in hours.
 *
 * <p>Its own context, because it is the one test that wants the reload check to run on <i>every</i>
 * request. In production the interval is minutes and the check rides a request precisely so it stays
 * rare; here that throttle is the thing in the way, so it is turned down to nothing.
 *
 * <p>Nothing in this test evicts anything by hand. That is the point — {@link CompanyCacheIntegrationTest}
 * proves the caches hold, and this proves they let go on their own.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
// 1ns, not a "small" interval: this test needs the probe to run on EVERY request, and any
// duration a request could finish inside reintroduces the race it is meant to exclude — at
// 10ms two consecutive MockMvc calls both landed in one window on CI, the second skipped its
// probe, and the eviction under test never fired. The throttle measures nanoTime, so 1ns is
// exact rather than approximate; zero is refused because it would read as "never cache".
@TestPropertySource(properties = "lightmove.company.cache.reload-check-interval=1ns")
class UniverseReloadWatchIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;
    @Autowired UniverseReloadWatch reloadWatch;

    private ApolloUniverse universe;

    @BeforeEach
    void freshUniverse() {
        universe = new ApolloUniverse(db, reloadWatch);
        universe.reset();
    }

    @Test
    @DisplayName("a company arriving by pipeline clears the caches without anyone evicting them")
    void aReloadEvictsWithoutBeingTold() throws Exception {
        String admin = adminOf("Reload Watch Firm");
        universe.company("c1", "One").industry("oil & energy").employees(10).insert();

        // Baselines the fingerprint and fills the cache. A first probe never evicts: on a cold start
        // there is nothing cached to protect, and "I have not looked before" is not a change.
        assertThat(energyCount(admin)).isEqualTo(1);

        // Exactly what the pipeline does, and nothing else — no evictCaches(), no reset().
        universe.company("c2", "Two").industry("oil & energy").employees(10).insert();

        assertThat(energyCount(admin)).isEqualTo(2);
    }

    @Test
    @DisplayName("a universe that has not moved keeps its caches")
    void anUnchangedUniverseKeepsItsCaches() throws Exception {
        // The other half of the contract: a check that finds nothing must be free. If the fingerprint
        // compared unequal to itself — a timestamp read back at a different precision, say — the
        // caches would be cleared on every request and the TTLs would mean nothing.
        String admin = adminOf("Reload Steady Firm");
        universe.company("c1", "One").industry("oil & energy").employees(10).insert();

        assertThat(energyCount(admin)).isEqualTo(1);

        // A change the fingerprint deliberately cannot see: the row count is unchanged and nothing
        // sets updated_at on this table (it has a default, not a trigger), so the pair has not moved.
        //
        // 'retail', not 'utilities' — the taxonomy puts 'utilities' in Energy & Utilities alongside
        // 'oil & energy', so that version of this test held at 1 whether the cache was cleared or not
        // and would have passed with @Cacheable deleted. Moving the row to another group means an
        // eviction drops this count to 0, so the assertion can actually fail.
        db.update("UPDATE app_lm_apollo_companies SET industry = 'retail' "
                + "WHERE apollo_account_id = 'c1'");

        assertThat(energyCount(admin)).isEqualTo(1);
    }

    private MvcResult facets(String token) throws Exception {
        return mvc.perform(get("/api/v1/companies/facets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
    }

    private long energyCount(String token) throws Exception {
        for (var group : body(facets(token)).get("sectorGroups")) {
            if ("Energy & Utilities".equals(group.get("name").asText())) {
                return group.get("count").asLong();
            }
        }
        throw new AssertionError("no Energy & Utilities group in the facets response");
    }

    private String adminOf(String workspaceName) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), workspaceName);
        return login("alok@" + domain);
    }
}
