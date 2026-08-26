package app.lightmove.api.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The filter sidebar's own read: what every accordion can offer and how many companies each chip
 * reaches, over the whole universe.
 *
 * <p>The universe is seeded per test — it is ETL reference data, empty in a fresh schema — and read
 * through the real HTTP endpoint as a workspace member.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class CompanyFacetIntegrationTest extends FlowTestSupport {

    private static final String KEYWORDS_URL = "/api/v1/companies/keywords";

    @Autowired JdbcTemplate db;

    private ApolloUniverse universe;

    @BeforeEach
    void freshUniverse() {
        universe = new ApolloUniverse(db);
        universe.reset();
    }

    @Test
    @DisplayName("industries arrive grouped by the taxonomy, each with its own count")
    void industriesArriveGrouped() throws Exception {
        String admin = adminOf("Facet Sector Firm");
        universe.company("a1", "One").industry("oil & energy").employees(10).insert();
        universe.company("a2", "Two").industry("oil & energy").employees(10).insert();
        universe.company("a3", "Three").industry("utilities").employees(10).insert();

        MvcResult result = mvc.perform(get("/api/v1/companies/facets")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();

        var energy = groupNamed(result, "Energy & Utilities");
        // The group carries no rolled-up total: the sidebar states a group by what is inside it.
        assertThat(energy.has("count")).isFalse();
        assertThat(industryCount(energy, "oil & energy")).isEqualTo(2);
        assertThat(industryCount(energy, "utilities")).isEqualTo(1);
    }

    @Test
    @DisplayName("an industry no company carries still renders, counting zero")
    void emptyIndustriesStillRender() throws Exception {
        String admin = adminOf("Facet Empty Sector Firm");
        universe.company("a1", "One").industry("oil & energy").employees(10).insert();

        MvcResult result = mvc.perform(get("/api/v1/companies/facets")
                        .header("Authorization", "Bearer " + admin))
                .andReturn();

        // A chip missing from the sidebar reads as "no such sector"; a zero reads as "none here".
        assertThat(industryCount(groupNamed(result, "Energy & Utilities"), "mining & metals")).isZero();
    }

    @Test
    @DisplayName("the countries are the ones the universe actually holds, largest first and uncounted")
    void countriesRankedBySizeAndUncounted() throws Exception {
        String admin = adminOf("Facet Country Firm");
        universe.company("a1", "One").country("United Arab Emirates").employees(10).insert();
        universe.company("a2", "Two").country("United Arab Emirates").employees(10).insert();
        universe.company("a3", "Three").country("Qatar").employees(10).insert();

        mvc.perform(get("/api/v1/companies/facets").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.countries.length()").value(2))
                .andExpect(jsonPath("$.countries[0].value").value("United Arab Emirates"))
                .andExpect(jsonPath("$.countries[1].value").value("Qatar"))
                // The size still decides the order — it is what puts the UAE first — but it is settled
                // in SQL and never reaches the pill, which carries a name and nothing else.
                .andExpect(jsonPath("$.countries[0].label").value("United Arab Emirates"))
                .andExpect(jsonPath("$.countries[0].count").doesNotExist());
    }

    @Test
    @DisplayName("employee bands are counted by the same bounds the filter runs on")
    void employeeBandsCountedByTheirOwnBounds() throws Exception {
        String admin = adminOf("Facet Employee Firm");
        universe.company("a1", "Tiny").employees(10).insert();
        universe.company("a2", "Small").employees(120).insert();
        universe.company("a3", "Edge").employees(1_000).insert();
        universe.company("a4", "Over").employees(1_001).insert();

        MvcResult result = mvc.perform(get("/api/v1/companies/facets")
                        .header("Authorization", "Bearer " + admin))
                .andReturn();

        // 1000 is the top of 501-1000 and 1001 the bottom of 1001-2000 — the bands abut without
        // overlapping, so a company on the boundary is counted once and in the lower band.
        assertThat(bandCount(result, "employeeBands", "1-10")).isEqualTo(1);
        assertThat(bandCount(result, "employeeBands", "101-200")).isEqualTo(1);
        assertThat(bandCount(result, "employeeBands", "501-1000")).isEqualTo(1);
        assertThat(bandCount(result, "employeeBands", "1001-2000")).isEqualTo(1);
    }

    @Test
    @DisplayName("the Unknown revenue band counts the companies that publish no figure")
    void unknownRevenueBandIsCounted() throws Exception {
        String admin = adminOf("Facet Revenue Firm");
        universe.company("a1", "Published").revenue(2_000_000_000L).employees(10).insert();
        universe.company("a2", "Silent").revenue(null).employees(10).insert();
        universe.company("a3", "Also silent").revenue(null).employees(10).insert();

        MvcResult result = mvc.perform(get("/api/v1/companies/facets")
                        .header("Authorization", "Bearer " + admin))
                .andReturn();

        // Nine rows in ten carry no revenue; without this chip they are unreachable and uncounted.
        assertThat(bandCount(result, "revenueBands", "unknown")).isEqualTo(2);
        assertThat(bandCount(result, "revenueBands", "1b-5b")).isEqualTo(1);
    }

    @Test
    @DisplayName("market segments are matched through keywords, and their counts overlap")
    void marketSegmentsOverlap() throws Exception {
        String admin = adminOf("Facet Segment Firm");
        universe.company("a1", "Both").keywords("b2b", "saas").employees(10).insert();
        universe.company("a2", "Just B2B").keywords("b2b").employees(10).insert();

        MvcResult result = mvc.perform(get("/api/v1/companies/facets")
                        .header("Authorization", "Bearer " + admin))
                .andReturn();

        // A company can hold several positions at once, so these sum to more than the universe.
        assertThat(bandCount(result, "marketSegments", "B2B")).isEqualTo(2);
        assertThat(bandCount(result, "marketSegments", "SaaS")).isEqualTo(1);
    }

    @Test
    @DisplayName("both spellings of a segment count as one")
    void segmentAliasesAreMerged() throws Exception {
        String admin = adminOf("Facet Alias Firm");
        universe.company("a1", "Hyphenated").keywords("e-commerce").employees(10).insert();
        universe.company("a2", "Plain").keywords("ecommerce").employees(10).insert();

        MvcResult result = mvc.perform(get("/api/v1/companies/facets")
                        .header("Authorization", "Bearer " + admin))
                .andReturn();

        assertThat(bandCount(result, "marketSegments", "E-commerce")).isEqualTo(2);
    }

    @Test
    @DisplayName("the picker searches by name, prefix matches first")
    void searchRanksPrefixMatchesFirst() throws Exception {
        String admin = adminOf("Facet Search Firm");
        universe.company("a1", "Gulf Power").employees(10).insert();
        universe.company("a2", "Power Gulf").employees(500).insert();

        mvc.perform(get("/api/v1/companies/search").param("q", "power")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies.length()").value(2))
                .andExpect(jsonPath("$.companies[0].companyName").value("Power Gulf"));
    }

    @Test
    @DisplayName("a blank query returns nothing rather than the head of the universe")
    void blankQueryReturnsNothing() throws Exception {
        String admin = adminOf("Facet Blank Query Firm");
        universe.company("a1", "Gulf Power").employees(10).insert();

        // Six arbitrary companies before a key is pressed suggests they were chosen for a reason.
        mvc.perform(get("/api/v1/companies/search").param("q", "   ")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies.length()").value(0));
    }

    @Test
    @DisplayName("an over-long query is rejected")
    void overLongQueryRejected() throws Exception {
        String admin = adminOf("Facet Long Query Firm");

        MvcResult result = mvc.perform(get("/api/v1/companies/search")
                        .param("q", "x".repeat(200))
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a prefix match beats a mid-word one, and a one-company keyword is not offered")
    void keywordSuggestionsRankPrefixAndDropTheTail() throws Exception {
        String admin = adminOf("Facet Keyword Firm");
        // Above the floor, and "enterprise saas" is the bigger slice of the two.
        for (int index = 0; index < 12; index++) {
            universe.company("big" + index, "Big " + index).industry("retail").employees(10)
                    .keywords("enterprise saas").insert();
        }
        for (int index = 0; index < 11; index++) {
            universe.company("mid" + index, "Mid " + index).industry("retail").employees(10)
                    .keywords("saas").insert();
        }
        universe.company("solo", "Solo").industry("retail").employees(10)
                .keywords("saas consultancy for yachts").insert();
        universe.refreshKeywordVocabulary();

        // Ranked on the prefix first: the bigger slice is the one the consultant did not type.
        mvc.perform(get(KEYWORDS_URL).param("q", "saas").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keywords.length()").value(2))
                .andExpect(jsonPath("$.keywords[0].value").value("saas"))
                .andExpect(jsonPath("$.keywords[1].value").value("enterprise saas"));

        // The third match reaches one company. Three keywords in four in the real universe look like
        // that, and they are that company's own marketing copy rather than an axis of the market.
        mvc.perform(get(KEYWORDS_URL).param("q", "yachts").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keywords.length()").value(0));
    }

    @Test
    @DisplayName("a query too short to narrow the scan is refused rather than answered")
    void shortKeywordQueryIsNotRun() throws Exception {
        String admin = adminOf("Facet Keyword Floor Firm");
        for (int index = 0; index < 12; index++) {
            universe.company("s" + index, "S " + index).industry("retail").employees(10)
                    .keywords("saas").insert();
        }
        universe.refreshKeywordVocabulary();

        mvc.perform(get(KEYWORDS_URL).param("q", "s").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keywords.length()").value(0));
        mvc.perform(get(KEYWORDS_URL).param("q", "sa").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keywords.length()").value(1));
    }

    @Test
    @DisplayName("a signed-out caller reads no facets")
    void anonymousCallerRejected() throws Exception {
        mvc.perform(get("/api/v1/companies/facets")).andExpect(status().isUnauthorized());
    }

    private tools.jackson.databind.JsonNode groupNamed(MvcResult result, String name) throws Exception {
        for (var group : body(result).get("sectorGroups")) {
            if (group.get("name").asText().equals(name)) {
                return group;
            }
        }
        throw new AssertionError("no sector group named " + name);
    }

    private static long industryCount(tools.jackson.databind.JsonNode group, String industry) {
        for (var entry : group.get("industries")) {
            if (entry.get("value").asText().equals(industry)) {
                return entry.get("count").asLong();
            }
        }
        throw new AssertionError("no industry " + industry + " in group " + group.get("name").asText());
    }

    private long bandCount(MvcResult result, String facet, String value) throws Exception {
        for (var entry : body(result).get(facet)) {
            if (entry.get("value").asText().equals(value)) {
                return entry.get("count").asLong();
            }
        }
        throw new AssertionError("no " + facet + " entry for " + value);
    }

    private String adminOf(String workspaceName) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), workspaceName);
        return login("alok@" + domain);
    }
}
