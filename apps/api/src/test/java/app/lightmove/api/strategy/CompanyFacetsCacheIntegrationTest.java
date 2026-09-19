package app.lightmove.api.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The facets read with its cache armed. Everywhere else in the suite it is switched off — one Spring
 * context serves the whole JVM and {@link CompanyFacetIntegrationTest} reseeds the universe between
 * cases — so this class turns it back on for a context of its own.
 */
@IntegrationTest
@TestPropertySource(properties = "lightmove.company.facets.cache-ttl=30m")
class CompanyFacetsCacheIntegrationTest extends FlowTestSupport {

    private static final String FACETS_URL = "/api/v1/companies/facets";

    @Autowired JdbcTemplate db;

    private ApolloUniverse universe;

    @BeforeEach
    void freshUniverse() {
        universe = new ApolloUniverse(db);
        universe.reset();
    }

    @Test
    @DisplayName("a second call inside the TTL answers without reading the universe again")
    void secondCallIsServedFromTheHeldResponse() throws Exception {
        String admin = adminOf("Facet Cache Firm");
        universe.company("a1", "One").industry("oil & energy").employees(10).insert();

        String first = bodyOf(admin);

        // Change the universe underneath it. Anything that re-ran the four aggregates would report
        // three companies where it reported one; an identical body is the proof that no SQL ran.
        universe.reset();
        universe.company("b1", "Two").industry("utilities").employees(500).insert();
        universe.company("b2", "Three").industry("utilities").employees(500).insert();
        universe.company("b3", "Four").industry("utilities").employees(500).insert();

        assertThat(bodyOf(admin)).isEqualTo(first);
    }

    @Test
    @DisplayName("the browser may hold it, a shared cache may not")
    void cacheControlIsPrivateAndNeverPublic() throws Exception {
        String admin = adminOf("Facet Cache Header Firm");
        universe.company("a1", "One").industry("oil & energy").employees(10).insert();

        MvcResult result = mvc.perform(get(FACETS_URL).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();

        // The endpoint is behind PROJECT_BROWSE, and a shared cache in front of an authenticated
        // endpoint is how one tenant's response reaches another. That these particular counts are
        // tenant-independent does not license `public` — the header states the gate, not the payload.
        String cacheControl = result.getResponse().getHeader("Cache-Control");
        assertThat(cacheControl).contains("private").doesNotContain("public");
        // Off the same TTL as the server's own, so a browser cannot hold a staler figure than it.
        assertThat(cacheControl).contains("max-age=1800");
    }

    private String bodyOf(String bearerToken) throws Exception {
        return mvc.perform(get(FACETS_URL).header("Authorization", "Bearer " + bearerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String adminOf(String workspaceName) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), workspaceName);
        return login("alok@" + domain);
    }
}
