package app.lightmove.api.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import java.sql.PreparedStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The company-universe reads the Strategy screen depends on. The universe is seeded per test (the
 * table is ETL reference data, empty in a fresh schema), then queried through the real HTTP endpoints
 * as a workspace member — sectors with counts, live tag suggestions, and the match estimate.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class CompanyReferenceIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;

    @BeforeEach
    void freshUniverse() {
        db.execute("DELETE FROM app_lm_companies");
    }

    @Test
    @DisplayName("the sectors endpoint returns distinct sectors with counts, most populous first, nulls excluded")
    void sectorsRankedByCountNullsExcluded() throws Exception {
        String admin = adminOf("Company Sectors Firm");
        company("Retail", "Grocery Retail", "Consumer Services");
        company("Retail", "Grocery Retail");
        company("Oil and Gas", "Energy");
        companyWithoutSector("Consumer Services");

        mvc.perform(get("/api/v1/companies/sectors").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectors.length()").value(2))
                .andExpect(jsonPath("$.sectors[0].name").value("Retail"))
                .andExpect(jsonPath("$.sectors[0].count").value(2))
                .andExpect(jsonPath("$.sectors[1].name").value("Oil and Gas"))
                .andExpect(jsonPath("$.sectors[1].count").value(1));
    }

    @Test
    @DisplayName("suggestions rank co-occurring tags by frequency and exclude ground the sectors already cover")
    void suggestionsRankTagsAndExcludeCoveredGround() throws Exception {
        String admin = adminOf("Company Suggest Firm");
        company("Retail", "Grocery Retail", "Consumer Services");
        company("Retail", "Grocery Retail");
        company("Retail", "Grocery Retail");
        company("Retail", "Consumer Services");
        company("Oil and Gas", "Energy");

        mvc.perform(get("/api/v1/companies/sectors/suggestions")
                        .param("sector", "Retail")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                // "Grocery Retail" (3) ranks above "Consumer Services" (2); "Retail" itself never appears.
                .andExpect(jsonPath("$.inferredTags[0].tag").value("Grocery Retail"))
                .andExpect(jsonPath("$.inferredTags[0].count").value(3))
                .andExpect(jsonPath("$.inferredTags[1].tag").value("Consumer Services"));
    }

    @Test
    @DisplayName("the estimate counts sector matches OR tag matches without double-counting")
    void estimateCountsUnionWithoutDoubleCounting() throws Exception {
        String admin = adminOf("Company Estimate Firm");
        company("Retail", "Grocery Retail");         // matches on sector
        company("Oil and Gas", "Grocery Retail");    // matches on tag
        company("Retail", "Grocery Retail");         // matches on both — counted once
        company("Construction", "Building Materials"); // matches on neither

        MvcResult result = mvc.perform(get("/api/v1/companies/estimate")
                        .param("sector", "Retail")
                        .param("tag", "Grocery Retail")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("an empty scope estimates zero")
    void emptyScopeEstimatesZero() throws Exception {
        String admin = adminOf("Company Empty Firm");
        company("Retail", "Grocery Retail");

        mvc.perform(get("/api/v1/companies/estimate").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("the company universe is not readable without authentication")
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/v1/companies/sectors"))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String adminOf(String workspaceName) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), workspaceName);
        return login("alok@" + domain);
    }

    private void company(String sector, String... tags) {
        insert(sector, tags);
    }

    private void companyWithoutSector(String... tags) {
        insert(null, tags);
    }

    /** The id column is GENERATED ALWAYS, so it is left out; source_id just has to be unique. */
    private void insert(String sector, String[] tags) {
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_lm_companies (source, source_id, name, primary_industry, industry_tags)
                    VALUES ('test', gen_random_uuid()::text, 'Test Co', ?, ?)""");
            ps.setString(1, sector);
            ps.setArray(2, connection.createArrayOf("text", tags));
            return ps;
        });
    }
}
