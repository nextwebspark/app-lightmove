package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Shortlisted is retired as a destination: every door that files or moves a company or an executive refuses it,
 * while a row already there stays and every read still answers the stage.
 */
@IntegrationTest
class ShortlistedRetiredIntegrationTest extends PipelineFlowSupport {

    @Test
    @DisplayName("every write door refuses Shortlisted with a 400, and nothing moves")
    void everyDoorRefuses() throws Exception {
        firm("Shortlist Retired Firm");
        String projectId = mandate("Chief Financial Officer");
        String triage = "/api/v1/projects/" + projectId + "/triage";
        String alshaya = capture(projectId, "Alshaya");
        String khaled = add(projectId, """
                {"triageCompanyId":"%s","fullName":"Khaled Al Beirouty"}""".formatted(alshaya)).get("id").asText();

        assertRefused(write(admin, "POST", triage, """
                {"apolloAccountId":"a1","status":"shortlisted"}"""));
        assertRefused(write(admin, "POST", triage + "/capture", """
                {"companyName":"Shortlisted Co","status":"shortlisted"}"""));
        assertRefused(write(admin, "POST", triage + "/bulk", """
                {"apolloAccountIds":["a1"],"status":"shortlisted"}"""));
        assertRefused(write(admin, "PATCH", triage + "/" + alshaya, """
                {"status":"shortlisted"}"""));
        assertRefused(write(admin, "PUT", candidatesUrl(projectId) + "/" + khaled + "/triage-status", """
                {"status":"shortlisted"}"""));

        assertThat(db.queryForObject("select count(*) from app_lm_project_triage_company where project_id = ?::uuid "
                + "and status = 'SHORTLISTED'", Long.class, projectId)).isZero();
        assertThat(db.queryForObject("select triage_status from app_lm_project_candidate where id = ?::uuid",
                String.class, khaled)).isEqualTo("IN_UNIVERSE");
    }

    @Test
    @DisplayName("a company already at Shortlisted stays there and is still read")
    void anOldShortlistIsStillRead() throws Exception {
        firm("Shortlist Legacy Firm");
        String projectId = mandate("Chief Financial Officer");
        String alshaya = capture(projectId, "Alshaya");
        db.update("update app_lm_project_triage_company set status = 'SHORTLISTED' where id = ?::uuid", alshaya);

        JsonNode page = body(mvc.perform(get("/api/v1/projects/" + projectId + "/triage")
                        .param("status", "shortlisted")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(page.at("/companies/0/companyName").asText()).isEqualTo("Alshaya");
        assertThat(page.at("/counts/shortlisted").asLong()).isEqualTo(1);
        assertThat(write(admin, "PATCH", "/api/v1/projects/" + projectId + "/triage/" + alshaya, """
                {"status":"inUniverse"}""").getResponse().getStatus()).isEqualTo(200);
    }

    private void assertRefused(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(400);
        assertThat(body(result).get("code").asText()).isEqualTo("VALIDATION_FAILED");
    }
}
