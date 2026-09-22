package app.lightmove.api.triagecompany;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingCompanyEnricher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * V68's door. A company AI Research found on the open web files with whatever a record supplied and
 * nothing else, and the two constraints either side of it still say what they said: a STRATEGY row
 * must carry a universe id, and a web-sourced row must not be handed to the vendor for research.
 */
@IntegrationTest
class WebCompanySourceIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;
    @Autowired private RecordingCompanyEnricher companyEnricher;

    private String adminToken;

    @Test
    @DisplayName("a web-sourced company files with no universe id, and is not researched")
    void aWebSourcedCompanyFilesWithoutAUniverseId() throws Exception {
        String projectId = mandate("Web Source Firm");

        JsonNode filed = body(mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Yellow Door Energy","source":"web",
                                 "companyLinkedinUrl":"https://www.linkedin.com/company/yellow-door-energy/",
                                 "sourceUrl":"https://example.test/gcc-solar-2026",
                                 "note":"Distributed solar"}"""))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(filed.get("source").asText()).isEqualTo("web");
        assertThat(filed.get("apolloAccountId").isNull()).isTrue();
        // Nothing the model said became a figure: discovery resolved this row and found no record,
        // so it files empty rather than filling in.
        assertThat(filed.get("industry").isNull()).isTrue();
        assertThat(filed.get("numEmployees").isNull()).isTrue();

        // A slug is present and still nothing is bought. Discovery already asked the vendor behind
        // its own cap; researching again here would spend once more per filed row, uncapped.
        assertThat(companyEnricher.fetchedSlugs()).isEmpty();
    }

    @Test
    @DisplayName("that same row cannot become a STRATEGY row, because it carries no universe id")
    void aWebRowCannotBecomeAStrategyRow() throws Exception {
        String projectId = mandate("Web Constraint Firm");

        String companyId = body(mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Shamal Energy","source":"web"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        // V34's apollo_source CHECK is what makes "the server resolved this snapshot" true of every
        // STRATEGY row, and V68 deliberately left it alone.
        assertThatThrownBy(() -> db.update(
                "UPDATE app_lm_project_triage_company SET source = 'STRATEGY' WHERE id = ?::uuid",
                companyId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("apollo_source_chk");
    }

    @Test
    @DisplayName("a caller still cannot supply a company as STRATEGY")
    void strategyIsStillTheServersDoorAlone() throws Exception {
        String projectId = mandate("Strategy Door Firm");

        mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Made Up Holdings","source":"strategy"}"""))
                .andExpect(status().isBadRequest());
    }

    private static String captureUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/triage/capture";
    }

    private String mandate(String firmName) throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        adminToken = login(alok);

        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Discovery Client"}"""))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Head of Energy"}
                                """.formatted(clientId)))
                .andReturn()).get("id").asText();
    }
}
