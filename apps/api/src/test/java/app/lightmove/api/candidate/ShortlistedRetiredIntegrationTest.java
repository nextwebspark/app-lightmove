package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Shortlisted is no Companies stage: a write or a read naming it is an unknown stage, and nothing moves. */
@IntegrationTest
class ShortlistedRetiredIntegrationTest extends PipelineFlowSupport {

    @Test
    @DisplayName("every door that files, moves or reads a stage refuses Shortlisted with a 400")
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
        assertRefused(read(triage + "?status=shortlisted"));
        assertRefused(read(candidatesUrl(projectId) + "?triageStatus=shortlisted"));
        assertRefused(read("/api/v1/projects/" + projectId + "/export/companies?status=shortlisted"));

        assertThat(db.queryForObject("select status from app_lm_project_triage_company where id = ?::uuid",
                String.class, alshaya)).isEqualTo("IN_UNIVERSE");
        assertThat(db.queryForObject("select triage_status from app_lm_project_candidate where id = ?::uuid",
                String.class, khaled)).isEqualTo("IN_UNIVERSE");
    }

    private MvcResult read(String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + admin)).andReturn();
    }

    private void assertRefused(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(400);
        assertThat(body(result).get("code").asText()).isEqualTo("VALIDATION_FAILED");
    }
}
