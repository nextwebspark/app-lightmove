package app.lightmove.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The Sourcing read end to end: the companies matching a project's saved Strategy scope (sector, and
 * employee/revenue bands AND'd together), paginated by name, with an empty scope returning an empty
 * page rather than the whole universe.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class SourcingFlowIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;

    @BeforeEach
    void freshUniverse() {
        db.execute("DELETE FROM app_lm_companies");
    }

    @Test
    @DisplayName("a project with no scope sources nothing, without touching the company universe")
    void noScopeSourcesNothing() throws Exception {
        String admin = adminOf("Sourcing Empty Firm");
        String projectId = project(admin);
        company("Alpha Retail", "Retail", 5, 1_000_000L);

        mvc.perform(get(sourcingUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies.length()").value(0))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    @DisplayName("sourcing matches the sector scope, narrowed by employee AND revenue bands")
    void sourcingMatchesSectorAndedWithSizeBands() throws Exception {
        String admin = adminOf("Sourcing Match Firm");
        String projectId = project(admin);
        company("Alpha Retail", "Retail", 5, 1_000_000L);       // in scope: sector + both bands
        company("Beta Retail", "Retail", 5, 30_000_000L);       // wrong revenue band
        company("Gamma Retail", "Retail", 300, 1_000_000L);     // wrong employee band
        company("Delta Energy", "Oil and Gas", 5, 1_000_000L);  // wrong sector

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");
        putCompanySize(admin, projectId, """
                {"employee":["1-10"],"revenue":["<5M"]}""");

        mvc.perform(get(sourcingUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.companies.length()").value(1))
                .andExpect(jsonPath("$.companies[0].name").value("Alpha Retail"));
    }

    @Test
    @DisplayName("pagination slices the matches in stable name order and reports the true total")
    void paginationSlicesInNameOrder() throws Exception {
        String admin = adminOf("Sourcing Page Firm");
        String projectId = project(admin);
        company("Alpha Retail", "Retail", 5, 1_000_000L);
        company("Bravo Retail", "Retail", 5, 1_000_000L);
        company("Charlie Retail", "Retail", 5, 1_000_000L);

        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");

        mvc.perform(get(sourcingUrl(projectId))
                        .param("page", "0").param("size", "2")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.companies.length()").value(2))
                .andExpect(jsonPath("$.companies[0].name").value("Alpha Retail"))
                .andExpect(jsonPath("$.companies[1].name").value("Bravo Retail"));

        mvc.perform(get(sourcingUrl(projectId))
                        .param("page", "1").param("size", "2")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.companies.length()").value(1))
                .andExpect(jsonPath("$.companies[0].name").value("Charlie Retail"));
    }

    @Test
    @DisplayName("another workspace's project is masked, even to a verified user")
    void crossTenantMasked() throws Exception {
        String admin = adminOf("Sourcing Masked Firm");
        String projectId = project(admin);
        String outsider = verifiedUser("Out Sider", "out@other-" + domain);

        MvcResult masked = mvc.perform(get(sourcingUrl(projectId))
                        .header("Authorization", "Bearer " + outsider))
                .andReturn();
        assertThat(masked.getResponse().getStatus()).isEqualTo(404);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String sourcingUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/sourcing";
    }

    private void putSectors(String token, String projectId, String bodyJson) throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/sectors")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isOk());
    }

    private void putCompanySize(String token, String projectId, String bodyJson) throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/company-size")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isOk());
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
                                {"name":"Sourcing Client"}"""))
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

    /** The id column is GENERATED ALWAYS, so it is left out; source_id just has to be unique. */
    private void company(String name, String sector, int employeeCount, long revenueUsd) {
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO app_lm_companies
                        (source, source_id, name, primary_industry, industry_tags, employee_count, revenue_usd)
                    VALUES ('test', gen_random_uuid()::text, ?, ?, ?, ?, ?)""");
            ps.setString(1, name);
            ps.setString(2, sector);
            ps.setArray(3, connection.createArrayOf("text", new String[0]));
            ps.setInt(4, employeeCount);
            ps.setLong(5, revenueUsd);
            return ps;
        });
    }
}
