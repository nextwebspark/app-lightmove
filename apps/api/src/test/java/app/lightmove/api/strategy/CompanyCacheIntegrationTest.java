package app.lightmove.api.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import app.lightmove.api.strategy.service.UniverseReloadWatch;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * That the universe caches are actually holding, and holding the right thing.
 *
 * <p>Every test here seeds a company <b>behind the cache's back</b> — straight into the table without
 * evicting — and then asserts the endpoint still answers with the old number. That shape is the point:
 * a test that seeds through {@code universe.reset()} and asserts the new number passes identically
 * with caching turned off, and would prove nothing. The stale answer <i>is</i> the evidence.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
// Pinned high rather than left on the production default: every test here needs the cache to survive
// a seed made behind its back, and a future tuning of that default would otherwise turn these into
// silent false passes rather than failures.
@TestPropertySource(properties = "lightmove.company.cache.reload-check-interval=1h")
class CompanyCacheIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;
    @Autowired UniverseReloadWatch reloadWatch;

    private ApolloUniverse universe;

    @BeforeEach
    void freshUniverse() {
        universe = new ApolloUniverse(db, reloadWatch);
        universe.reset();
    }

    @Test
    @DisplayName("facets are served from cache, and an eviction is what lets a new company through")
    void facetsAreCachedUntilEvicted() throws Exception {
        String admin = adminOf("Cache Facets Firm");
        universe.company("c1", "One").industry("oil & energy").employees(10).insert();
        universe.company("c2", "Two").industry("oil & energy").employees(10).insert();

        assertThat(energyCount(admin)).isEqualTo(2);

        // Behind the cache's back: no eviction, so the endpoint must not notice.
        universe.company("c3", "Three").industry("oil & energy").employees(10).insert();
        assertThat(energyCount(admin)).isEqualTo(2);

        universe.evictCaches();
        assertThat(energyCount(admin)).isEqualTo(3);
    }

    @Test
    @DisplayName("the five facet sections keep their own entries rather than collapsing onto one")
    void everyFacetSectionKeepsItsOwnEntry() throws Exception {
        // The regression test for the trap this cache was nearly built with. All five facet methods
        // take no arguments, and Spring keys a zero-argument method as SimpleKey.EMPTY — so sharing
        // one cache without naming a key gives all five the entry whichever ran first wrote, and
        // sectorGroups() starts answering with country facets. Nothing throws; the screen is just
        // wrong. Reading all five in one response is what would catch it.
        String admin = adminOf("Cache Facet Keys Firm");
        universe.company("c1", "One").industry("oil & energy").country("Saudi Arabia")
                .employees(10).keywords("saas").insert();

        JsonNode facets = body(facets(admin));

        assertThat(facets.get("sectorGroups")).isNotNull();
        assertThat(facets.get("sectorGroups").get(0).has("industries")).isTrue();
        assertThat(labels(facets.get("countries"))).contains("Saudi Arabia");
        assertThat(labels(facets.get("marketSegments"))).contains("SaaS");
        assertThat(labels(facets.get("employeeBands"))).isNotEmpty();
        assertThat(labels(facets.get("revenueBands"))).contains("Unknown");

        // Every section distinct, compared pairwise. Chaining both isNotEqualTo onto one subject —
        // which is what this did first — only ever compares that subject, so the marketSegments and
        // revenueBands pair went unchecked while the comment claimed otherwise.
        List<List<String>> sections = List.of(
                labels(facets.get("marketSegments")),
                labels(facets.get("countries")),
                labels(facets.get("employeeBands")),
                labels(facets.get("revenueBands")));
        for (int i = 0; i < sections.size(); i++) {
            for (int j = i + 1; j < sections.size(); j++) {
                assertThat(sections.get(i)).isNotEqualTo(sections.get(j));
            }
        }
    }

    @Test
    @DisplayName("a typeahead ignores the caller's capitalisation, sharing one entry")
    void typeaheadKeyIgnoresCase() throws Exception {
        String admin = adminOf("Cache Typeahead Firm");
        universe.company("c1", "Saudi Aramco").employees(10).insert();

        assertThat(suggestionCount(admin, "SAUDI")).isEqualTo(1);

        // Both matches are ILIKE, so these two queries have identical answers and must share one
        // entry. Seeded behind the cache's back: if the key were case-sensitive, "saudi" would miss
        // and go to the database, where the second company now is.
        universe.company("c2", "Saudi Telecom").employees(10).insert();
        assertThat(suggestionCount(admin, "saudi")).isEqualTo(1);
        assertThat(suggestionCount(admin, "SaUdI")).isEqualTo(1);

        universe.evictCaches();
        assertThat(suggestionCount(admin, "saudi")).isEqualTo(2);
    }

    @Test
    @DisplayName("the filtered total is computed once and reused across page turns")
    void scopeCountIsReusedAcrossPages() throws Exception {
        String admin = adminOf("Cache Scope Firm");
        String projectId = project(admin);
        universe.company("c1", "One").employees(10).insert();
        universe.company("c2", "Two").employees(20).insert();

        assertThat(totalOnPage(admin, projectId, 0)).isEqualTo(2);

        // A different page is a different key for the rows, but the same key for the total — so the
        // count must come back cached even though this page has never been asked for before.
        universe.company("c3", "Three").employees(30).insert();
        assertThat(totalOnPage(admin, projectId, 1)).isEqualTo(2);

        universe.evictCaches();
        assertThat(totalOnPage(admin, projectId, 0)).isEqualTo(3);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private MvcResult facets(String token) throws Exception {
        return mvc.perform(get("/api/v1/companies/facets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
    }

    private long energyCount(String token) throws Exception {
        for (JsonNode group : body(facets(token)).get("sectorGroups")) {
            if ("Energy & Utilities".equals(group.get("name").asText())) {
                return group.get("count").asLong();
            }
        }
        throw new AssertionError("no Energy & Utilities group in the facets response");
    }

    private int suggestionCount(String token, String query) throws Exception {
        return body(mvc.perform(get("/api/v1/companies/search?q=" + query)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()).get("companies").size();
    }

    private long totalOnPage(String token, String projectId, int page) throws Exception {
        return body(mvc.perform(get("/api/v1/projects/" + projectId + "/strategy/companies?page=" + page)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()).get("totalCount").asLong();
    }

    private static List<String> labels(JsonNode facetArray) {
        List<String> labels = new ArrayList<>();
        facetArray.forEach(facet -> labels.add(facet.get("label").asText()));
        return labels;
    }

    private String adminOf(String workspaceName) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), workspaceName);
        return login("alok@" + domain);
    }

    private String project(String token) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Cache Client"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Head of Retail"}
                                """.formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }
}
