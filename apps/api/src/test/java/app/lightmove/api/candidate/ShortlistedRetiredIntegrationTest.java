package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import app.lightmove.api.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** Shortlisted is no Companies stage: a write naming it lands in universe, a read naming it is an unknown stage. */
@IntegrationTest
class ShortlistedRetiredIntegrationTest extends PipelineFlowSupport {

    @Test
    @DisplayName("a write naming Shortlisted lands in universe, for extension builds from before it; a read still refuses it")
    void writesLandInUniverseReadsRefuse() throws Exception {
        firm("Shortlist Retired Firm");
        String projectId = mandate("Chief Financial Officer");
        String triage = "/api/v1/projects/" + projectId + "/triage";
        String alshaya = capture(projectId, "Alshaya");
        String khaled = add(projectId, """
                {"triageCompanyId":"%s","fullName":"Khaled Al Beirouty"}""".formatted(alshaya)).get("id").asText();

        MvcResult captured = write(admin, "POST", triage + "/capture", """
                {"companyName":"Shortlisted Co","status":"shortlisted"}""");
        assertThat(captured.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(captured).get("status").asText()).isEqualTo("inUniverse");
        assertThat(write(admin, "POST", triage + "/bulk", """
                {"apolloAccountIds":["a1"],"status":"shortlisted"}""").getResponse().getStatus()).isNotEqualTo(400);

        write(admin, "PATCH", triage + "/" + alshaya, """
                {"status":"declined"}""");
        write(admin, "PATCH", triage + "/" + alshaya, """
                {"status":"shortlisted"}""");
        assertThat(db.queryForObject("select status from app_lm_project_triage_company where id = ?::uuid",
                String.class, alshaya)).isEqualTo("IN_UNIVERSE");

        write(admin, "PUT", candidatesUrl(projectId) + "/" + khaled + "/triage-status", """
                {"status":"declined"}""");
        write(admin, "PUT", candidatesUrl(projectId) + "/" + khaled + "/triage-status", """
                {"status":"shortlisted"}""");
        assertThat(db.queryForObject("select triage_status from app_lm_project_candidate where id = ?::uuid",
                String.class, khaled)).isEqualTo("IN_UNIVERSE");

        assertRefused(read(triage + "?status=shortlisted"));
        assertRefused(read(candidatesUrl(projectId) + "?triageStatus=shortlisted"));
        assertRefused(read("/api/v1/projects/" + projectId + "/export/companies?status=shortlisted"));
    }

    @Test
    @DisplayName("a row the previous revision writes at Shortlisted mid-rollout is left out of every read, never a 500")
    void leavesAStragglerOut() throws Exception {
        firm("Shortlist Straggler Firm");
        String projectId = mandate("Chief Financial Officer");
        String triage = "/api/v1/projects/" + projectId + "/triage";
        String alshaya = capture(projectId, "Alshaya");
        String harbour = capture(projectId, "Harbour Ports");
        String khaled = add(projectId, """
                {"triageCompanyId":"%s","fullName":"Khaled Al Beirouty"}""".formatted(alshaya)).get("id").asText();
        add(projectId, """
                {"triageCompanyId":"%s","fullName":"Lina Haddad"}""".formatted(harbour));
        JsonNode omar = add(projectId, """
                {"triageCompanyId":"%s","fullName":"Omar Straggler"}""".formatted(alshaya));
        db.update("update app_lm_project_triage_company set status = 'SHORTLISTED' where id = ?::uuid", harbour);
        db.update("update app_lm_project_candidate set triage_status = 'SHORTLISTED' where id = ?::uuid",
                omar.get("id").asText());

        for (String url : List.of(triage + "?status=inUniverse", triage + "?status=declined",
                triage + "?status=inUniverse&search=al", triage + "?status=inUniverse&sort=executiveStatus",
                triage + "?status=inUniverse&sort=executiveStatus&search=khaled", candidatesUrl(projectId),
                "/api/v1/projects/" + projectId + "/talent-map", "/api/v1/projects/" + projectId + "/report",
                "/api/v1/projects/" + projectId + "/pipeline",
                "/api/v1/candidates/" + omar.get("personId").asText())) {
            MvcResult result = read(url);
            assertThat(result.getResponse().getStatus()).as(url + " " + result.getResponse().getContentAsString())
                    .isEqualTo(200);
            assertThat(result.getResponse().getContentAsString()).as(url).doesNotContain(omar.get("id").asText());
        }
        List<String> listed = new ArrayList<>();
        body(read(triage + "?status=inUniverse")).get("companies").forEach(row -> listed.add(row.get("id").asText()));
        assertThat(listed).containsExactly(alshaya);
        assertThat(body(read(candidatesUrl(projectId))).get("candidates").findValuesAsString("id"))
                .contains(khaled);
        MvcResult export = read("/api/v1/projects/" + projectId + "/export/companies?search=al");
        assertThat(export.getResponse().getStatus()).isEqualTo(200);
        assertThat(export.getResponse().getContentAsString()).contains("Khaled Al Beirouty")
                .doesNotContain("Omar Straggler").doesNotContain("Harbour Ports");
    }

    private MvcResult read(String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + admin)).andReturn();
    }

    private void assertRefused(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(400);
        assertThat(body(result).get("code").asText()).isEqualTo("VALIDATION_FAILED");
    }
}
