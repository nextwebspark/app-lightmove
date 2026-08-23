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
 * <p>Its own context: the one test that wants the reload check on every request. Nothing here evicts
 * by hand — {@link CompanyCacheIntegrationTest} proves the caches hold, this proves they let go.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
// 1ns so every request probes. Any duration a request could finish inside reintroduces the race:
// at 10ms two consecutive calls landed in one window on CI and the eviction never fired.
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

        // A first probe never evicts: "I have not looked before" is not a change.
        assertThat(energyCount(admin)).isEqualTo(1);

        universe.company("c2", "Two").industry("oil & energy").employees(10).insert();

        assertThat(energyCount(admin)).isEqualTo(2);
    }

    @Test
    @DisplayName("a universe that has not moved keeps its caches")
    void anUnchangedUniverseKeepsItsCaches() throws Exception {
        String admin = adminOf("Reload Steady Firm");
        universe.company("c1", "One").industry("oil & energy").employees(10).insert();

        assertThat(energyCount(admin)).isEqualTo(1);

        // Invisible to the fingerprint: same row count, and nothing sets updated_at here. 'retail'
        // rather than 'utilities', which shares a taxonomy group with 'oil & energy' and would have
        // held at 1 either way — an eviction must be able to drop this to 0.
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
